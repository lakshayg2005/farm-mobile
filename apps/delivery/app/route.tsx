/**
 * Delivery app: today's route (farm-mobile #6, real data in #28).
 *
 * Loads the signed-in partner's pool from GET /logistics/my-route and lists its stops in driving
 * order (pickups before the drops that depend on them). "Navigate" deep-links to Google Maps;
 * pickup and delivery go through their modals to the logistics API. Open with ?date=YYYY-MM-DD to
 * see another day's route.
 * A map thumbnail placeholder sits at the top (react-native-maps comes later).
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  Linking,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  RefreshControl,
} from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Text, Card } from '@farm/ui';
import { getMyRoute, useAuth, type Pool } from '@farm/api';
import { useLocationPings } from '../src/hooks/useLocationPings';
import { ProofOfDeliveryModal } from '../src/components/ProofOfDeliveryModal';
import { PickupModal } from '../src/components/PickupModal';
import {
  localISODate,
  markStopDone,
  routeStops,
  routeSummary,
  type RouteStop,
  type StopStatus,
  type StopType,
} from '../src/route/route';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function mapsUrl(lat: number, lng: number, label: string): string {
  const encoded = encodeURIComponent(label);
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&destination_place_id=${encoded}&travelmode=driving`;
}

function stopTypeColor(type: StopType): string {
  return type === 'pickup' ? '#1b5e20' : '#1565c0';
}

function stopTypeBg(type: StopType): string {
  return type === 'pickup' ? '#e8f5e9' : '#e3f2fd';
}

function statusIcon(status: StopStatus): string {
  if (status === 'done') return '✅';
  if (status === 'skipped') return '⏭️';
  return '⏳';
}

// ─── Sub-components ───────────────────────────────────────────────────────────

interface StopCardProps {
  stop: RouteStop;
  isNext: boolean;
  onNavigate: (stop: RouteStop) => void;
  onAction: (stop: RouteStop) => void;
}

function StopCard({ stop, isNext, onNavigate, onAction }: StopCardProps) {
  const isDone = stop.status === 'done';
  const color = stopTypeColor(stop.type);
  const bg = stopTypeBg(stop.type);

  return (
    <Card style={{ ...styles.stopCard, ...(isDone ? styles.stopCardDone : {}) }}>
      {/* Header row */}
      <View style={styles.stopHeader}>
        <View style={[styles.seqBadge, { backgroundColor: isDone ? '#ccc' : color }]}>
          <Text style={styles.seqText}>{stop.seq}</Text>
        </View>
        <View style={{ flex: 1, marginLeft: 10 }}>
          <View style={styles.typePill}>
            <View style={[styles.typeDot, { backgroundColor: bg, borderColor: color }]}>
              <Text style={[styles.typeLabel, { color }]}>
                {stop.type === 'pickup' ? '📦 PICKUP' : '🚪 DROP'}
              </Text>
            </View>
            {isNext && (
              <View style={styles.nextBadge}>
                <Text style={styles.nextBadgeText}>NEXT</Text>
              </View>
            )}
            <Text style={styles.statusIcon}>{statusIcon(stop.status)}</Text>
          </View>
          <Text variant="h2" style={[styles.stopLabel, isDone && styles.textDone]}>
            {stop.label}
          </Text>
        </View>
      </View>

      {/* Address */}
      <Text variant="caption" style={styles.address}>
        📍 {stop.address}
      </Text>

      {/* Items */}
      <View style={styles.itemsBox}>
        <Text variant="label">Items</Text>
        {stop.items.map((item, i) => (
          <Text key={i} variant="caption" style={styles.itemRow}>
            • {item}
          </Text>
        ))}
      </View>

      {/* Demo OTP display for testing drops */}
      {stop.type === 'drop' && stop.otp && !isDone && (
        <View style={styles.otpBox}>
          <Text variant="label" style={{ color: '#7b1fa2' }}>
            Customer OTP (Demo Code)
          </Text>
          <Text style={styles.otpValue}>{stop.otp}</Text>
          <Text variant="caption" style={{ color: '#7b1fa2', marginTop: 2 }}>
            In production, customer shares this upon delivery
          </Text>
        </View>
      )}

      {/* Action buttons */}
      {!isDone && (
        <View style={styles.stopActions}>
          <Button
            label="🗺️  Navigate"
            variant="secondary"
            style={styles.navBtn}
            onPress={() => onNavigate(stop)}
          />
          <Button
            label={stop.type === 'pickup' ? '📦 Verify Pickup' : '📸 Deliver (Photo + OTP)'}
            style={styles.doneBtn}
            onPress={() => onAction(stop)}
          />
        </View>
      )}
    </Card>
  );
}

// ─── Mock Map Banner ─────────────────────────────────────────────────────────

function MapBanner({ totalStops, doneStops }: { totalStops: number; doneStops: number }) {
  const pct = totalStops > 0 ? Math.round((doneStops / totalStops) * 100) : 0;
  return (
    <View style={styles.mapBanner}>
      <View style={styles.mapPlaceholder}>
        <Text style={styles.mapEmoji}>🗺️</Text>
        <Text variant="caption" style={{ color: '#fff', marginTop: 4 }}>
          Map view (react-native-maps — Sprint 3)
        </Text>
      </View>
      {/* Progress bar */}
      <View style={styles.progressOuter}>
        <View style={[styles.progressInner, { width: `${pct}%` as `${number}%` }]} />
      </View>
      <Text variant="caption" style={styles.progressLabel}>
        {doneStops} / {totalStops} stops completed ({pct}%)
      </Text>
    </View>
  );
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

function formatDay(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
}

export default function TodaysRouteScreen() {
  const { isAuthenticated, user, isLoading } = useAuth();
  const isPartner = isAuthenticated && !!user?.roles?.includes('delivery');
  const params = useLocalSearchParams<{ date?: string }>();
  const today = localISODate();
  const poolDate = params.date && /^\d{4}-\d{2}-\d{2}$/.test(params.date) ? params.date : today;

  const queryClient = useQueryClient();
  const route = useQuery({
    queryKey: ['my-route', poolDate],
    queryFn: () => getMyRoute(poolDate),
    enabled: isPartner,
  });

  const [activeDeliveryStop, setActiveDeliveryStop] = useState<RouteStop | null>(null);
  const [activePickupStop, setActivePickupStop] = useState<RouteStop | null>(null);
  const [successToast, setSuccessToast] = useState<string | null>(null);

  // Derived state
  const stops = routeStops(route.data ?? null);
  const summary = routeSummary(stops);
  const nextStop = summary.next;

  // Issue #7: location pings every 30s while the route still has stops to work
  const { isTracking, lastPingTime, pingCount, error: trackingError } = useLocationPings(
    Boolean(nextStop),
  );

  const handleNavigate = useCallback(async (stop: RouteStop) => {
    const url = mapsUrl(stop.lat, stop.lng, stop.label);
    try {
      const supported = await Linking.canOpenURL(url);
      if (supported) {
        await Linking.openURL(url);
      } else {
        Alert.alert('Maps not available', 'Could not open Google Maps on this device.');
      }
    } catch {
      Alert.alert('Navigation error', 'Failed to launch map navigation.');
    }
  }, []);

  const handleStopAction = useCallback((stop: RouteStop) => {
    if (stop.type === 'pickup') {
      setActivePickupStop(stop);
    } else {
      setActiveDeliveryStop(stop);
    }
  }, []);

  // The modals call this only after logistics accepted the pickup or delivery.
  const handleStopSuccess = useCallback(
    (stopId: string) => {
      const key = ['my-route', poolDate];
      queryClient.setQueryData<Pool | null>(
        key,
        (pool: Pool | null | undefined) => markStopDone(pool, stopId) ?? null,
      );
      void queryClient.invalidateQueries({ queryKey: key });
      setSuccessToast('Stop status updated successfully!');
      setTimeout(() => setSuccessToast(null), 3500);
    },
    [queryClient, poolDate],
  );

  // Guard: must be authenticated delivery partner
  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }

  if (!isPartner) {
    return (
      <View style={styles.center}>
        <Text variant="body">Access denied. Please log in as a delivery partner.</Text>
        <Button
          label="Go Back"
          variant="secondary"
          style={{ marginTop: 16 }}
          onPress={() => router.replace('/')}
        />
      </View>
    );
  }

  const dayLabel = poolDate === today ? 'today' : formatDay(poolDate);
  // With no pool on the asked day, logistics answers with the partner's active pool from another day.
  const shownDate = route.data?.poolDate ?? poolDate;

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <Text variant="h2" style={styles.headerTitle}>
          {shownDate === today ? "Today's Route" : `Route · ${formatDay(shownDate)}`}
        </Text>
        <View style={{ width: 36 }} />
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={route.isRefetching}
            onRefresh={() => route.refetch()}
            tintColor="#2d6a4f"
          />
        }
      >
        {route.isPending ? (
          <ActivityIndicator size="large" color="#2d6a4f" style={{ marginTop: 48 }} />
        ) : route.isError ? (
          <Card style={styles.allDoneCard}>
            <Text variant="h2">Could not load your route</Text>
            <Text variant="caption" style={{ marginTop: 4 }}>
              {route.error instanceof Error ? route.error.message : 'Try again in a moment.'}
            </Text>
            <Button label="Try again" style={{ marginTop: 12 }} onPress={() => route.refetch()} />
          </Card>
        ) : stops.length === 0 ? (
          <Card style={styles.allDoneCard}>
            <Text style={styles.allDoneText}>No route assigned for {dayLabel}.</Text>
            <Text variant="caption" style={{ marginTop: 4 }}>
              Pull down to check again once dispatch has assigned you a pool.
            </Text>
          </Card>
        ) : (
          <>
            {/* Map banner */}
            <MapBanner totalStops={stops.length} doneStops={summary.done} />

            {/* Issue #7: 30-Second Location Tracking Status Banner */}
            <View style={styles.trackingCard}>
              <View style={styles.trackingHeader}>
                <View
                  style={[
                    styles.trackingDot,
                    isTracking ? styles.trackingDotActive : styles.trackingDotInactive,
                  ]}
                />
                <Text style={styles.trackingTitle}>
                  {isTracking ? 'GPS Tracking Active' : 'GPS Tracking Paused'}
                </Text>
                <View style={styles.pingPill}>
                  <Text style={styles.pingPillText}>30s interval</Text>
                </View>
              </View>
              <Text variant="caption" style={styles.trackingSubtitle}>
                {isTracking
                  ? `Reporting coordinates to logistics dispatch · ${pingCount} pings sent${
                      lastPingTime ? ` (last: ${lastPingTime})` : ''
                    }`
                  : 'Tracking resumes automatically when pending stops exist.'}
              </Text>
              {trackingError && (
                <Text variant="caption" style={styles.trackingErrorText}>
                  ⚠️ {trackingError}
                </Text>
              )}
            </View>

            {/* Success Feedback Toast */}
            {successToast && (
              <View style={styles.toastBox}>
                <Text style={styles.toastText}>🎉 {successToast}</Text>
              </View>
            )}

            {/* Route summary */}
            <View style={styles.summaryRow}>
              <View style={styles.summaryChip}>
                <Text style={styles.summaryNum}>{summary.pickups}</Text>
                <Text variant="caption">Pickups</Text>
              </View>
              <View style={styles.summaryChip}>
                <Text style={styles.summaryNum}>{summary.drops}</Text>
                <Text variant="caption">Drops</Text>
              </View>
              <View style={styles.summaryChip}>
                <Text style={[styles.summaryNum, { color: '#2d6a4f' }]}>{summary.done}</Text>
                <Text variant="caption">Done</Text>
              </View>
            </View>

            {/* All-done banner */}
            {summary.finished && (
              <Card style={styles.allDoneCard}>
                <Text style={styles.allDoneText}>🎉 Route complete! Great work today.</Text>
              </Card>
            )}

            {/* Stop list */}
            {stops.map(stop => (
              <StopCard
                key={stop.id}
                stop={stop}
                isNext={nextStop?.id === stop.id}
                onNavigate={handleNavigate}
                onAction={handleStopAction}
              />
            ))}
          </>
        )}

        <View style={{ height: 32 }} />
      </ScrollView>

      {/* Issue #7: Proof of Delivery Modal (Photo + OTP) */}
      <ProofOfDeliveryModal
        visible={Boolean(activeDeliveryStop)}
        stop={activeDeliveryStop}
        onClose={() => setActiveDeliveryStop(null)}
        onSuccess={handleStopSuccess}
      />

      {/* Issue #7: Pickup Confirmation Modal */}
      <PickupModal
        visible={Boolean(activePickupStop)}
        stop={activePickupStop}
        onClose={() => setActivePickupStop(null)}
        onSuccess={handleStopSuccess}
      />
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#f5f5f0' },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: '#f5f5f0',
  },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2d6a4f',
    paddingTop: 52,
    paddingBottom: 14,
    paddingHorizontal: 16,
    justifyContent: 'space-between',
  },
  headerTitle: { color: '#fff', fontSize: 18 },
  backBtn: { width: 36, alignItems: 'flex-start' },
  backArrow: { color: '#fff', fontSize: 22 },

  // Scroll
  scroll: { padding: 16, paddingTop: 12 },

  // Map banner
  mapBanner: {
    borderRadius: 14,
    overflow: 'hidden',
    marginBottom: 14,
  },
  mapPlaceholder: {
    backgroundColor: '#1b5e20',
    height: 140,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mapEmoji: { fontSize: 44 },

  // Progress
  progressOuter: {
    height: 6,
    backgroundColor: '#c8e6c9',
  },
  progressInner: {
    height: 6,
    backgroundColor: '#2d6a4f',
  },
  progressLabel: {
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 2,
    color: '#555',
  },

  // Issue #7: GPS Tracking Card
  trackingCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#e0e0e0',
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  trackingHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  trackingDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: 8,
  },
  trackingDotActive: {
    backgroundColor: '#2e7d32',
  },
  trackingDotInactive: {
    backgroundColor: '#9e9e9e',
  },
  trackingTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1b1b1b',
    flex: 1,
  },
  pingPill: {
    backgroundColor: '#e8f5e9',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  pingPillText: {
    color: '#2e7d32',
    fontSize: 11,
    fontWeight: '600',
  },
  trackingSubtitle: {
    color: '#666',
    marginTop: 4,
  },
  trackingErrorText: {
    color: '#c62828',
    marginTop: 4,
  },

  // Toast
  toastBox: {
    backgroundColor: '#e8f5e9',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#a5d6a7',
    alignItems: 'center',
  },
  toastText: {
    color: '#1b5e20',
    fontWeight: '600',
    fontSize: 13,
  },

  // Summary chips
  summaryRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 14,
  },
  summaryChip: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 10,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  summaryNum: { fontSize: 22, fontWeight: '700', color: '#1b1b1b' },

  // All-done
  allDoneCard: {
    backgroundColor: '#e8f5e9',
    marginBottom: 14,
    alignItems: 'center',
  },
  allDoneText: { fontSize: 15, fontWeight: '600', color: '#2d6a4f' },

  // Stop card
  stopCard: {
    marginBottom: 12,
  },
  stopCardDone: {
    opacity: 0.6,
  },
  stopHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  seqBadge: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  seqText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  typePill: { flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 3 },
  typeDot: {
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  typeLabel: { fontSize: 10, fontWeight: '700' },
  nextBadge: {
    backgroundColor: '#ff6f00',
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  nextBadgeText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  statusIcon: { fontSize: 16, marginLeft: 'auto' },
  stopLabel: { fontSize: 15, fontWeight: '600', color: '#1b1b1b' },
  textDone: { textDecorationLine: 'line-through', color: '#888' },
  address: { color: '#555', marginBottom: 8 },

  // Items
  itemsBox: {
    backgroundColor: '#fafafa',
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#eee',
  },
  itemRow: { marginTop: 2, color: '#444' },

  // OTP
  otpBox: {
    backgroundColor: '#f3e5f5',
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#ce93d8',
  },
  otpValue: {
    fontSize: 28,
    fontWeight: '700',
    color: '#6a1b9a',
    letterSpacing: 6,
    marginTop: 4,
  },

  // Stop actions
  stopActions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
  navBtn: { flex: 1 },
  doneBtn: { flex: 1 },
});
