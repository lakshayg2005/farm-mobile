/**
 * Customer app: Order detail and status timeline (farm-mobile #5, real data in #27).
 * Shows a vertical stepper with the order's stages from the order service.
 * The current stage is highlighted; completed ones are ticked and timed; future ones are dim.
 */

import React from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { Button, Text, Card } from '@farm/ui';
import { useAuth } from '@farm/api';
import type { OrderStatus } from '@farm/api/src/order';
import {
  STATUS_META,
  TIMELINE_STEPS,
  addressLabel,
  orderTitle,
  rupees,
  shortId,
  stepIndex,
  stepTimes,
  useOrder,
} from '../src/orders/index.ts';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// ─── Sub-components ───────────────────────────────────────────────────────────

interface TimelineStepProps {
  stepStatus: OrderStatus;
  currentIndex: number;
  stepIndex: number;
  isLast: boolean;
  at: string | null;
}

function TimelineStep({ stepStatus, currentIndex, stepIndex, isLast, at }: TimelineStepProps) {
  const meta = STATUS_META[stepStatus];
  const isDone = stepIndex < currentIndex;
  const isCurrent = stepIndex === currentIndex;
  const isFuture = stepIndex > currentIndex;

  const dotColor = isDone ? '#2d6a4f' : isCurrent ? meta.color : '#ccc';
  const lineColor = isDone ? '#2d6a4f' : '#e0e0e0';

  return (
    <View style={styles.stepRow}>
      {/* Left: dot + connecting line */}
      <View style={styles.stepLeft}>
        <View style={[styles.stepDot, { backgroundColor: dotColor, borderColor: dotColor }]}>
          {isDone ? (
            <Text style={styles.stepDotText}>✓</Text>
          ) : isCurrent ? (
            <Text style={styles.stepDotText}>{meta.emoji}</Text>
          ) : (
            <View style={styles.stepDotEmpty} />
          )}
        </View>
        {!isLast && <View style={[styles.stepLine, { backgroundColor: lineColor }]} />}
      </View>

      {/* Right: label + when */}
      <View style={[styles.stepContent, isFuture && styles.stepFuture]}>
        <Text
          variant="label"
          style={[
            styles.stepLabel,
            isCurrent && { color: meta.color, fontWeight: '700' },
            isDone && { color: '#2d6a4f' },
            isFuture && { color: '#bbb' },
          ]}
        >
          {meta.label}
        </Text>
        {!isFuture && at && (
          <Text variant="caption" style={{ color: isCurrent ? meta.color : '#777', marginTop: 2 }}>
            {formatDateTime(at)}
          </Text>
        )}
        {isCurrent && !at && (
          <Text variant="caption" style={{ color: meta.color, marginTop: 2 }}>
            Current status
          </Text>
        )}
      </View>
    </View>
  );
}

function CancelledBanner() {
  return (
    <Card style={styles.cancelledBanner}>
      <Text style={styles.cancelledEmoji}>❌</Text>
      <Text variant="h2" style={styles.cancelledTitle}>Order Cancelled</Text>
      <Text variant="caption" style={styles.cancelledSub}>
        This order was cancelled. Any payment is refunded to you.
      </Text>
    </Card>
  );
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

export default function OrderDetailScreen() {
  const { isAuthenticated, user, isLoading } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  const orderQuery = useOrder(id ?? '');

  if (isLoading || orderQuery.isPending) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }

  if (!isAuthenticated || !user?.roles?.includes('customer')) {
    return (
      <View style={styles.center}>
        <Text variant="body">Access denied.</Text>
        <Button label="Go Home" variant="secondary" style={{ marginTop: 16 }} onPress={() => router.replace('/')} />
      </View>
    );
  }

  const order = orderQuery.data;

  if (!order) {
    const notFound = orderQuery.error?.message === 'Order not found';
    return (
      <View style={styles.center}>
        <Text variant="h2">{notFound ? 'Order not found' : 'Could not load the order'}</Text>
        {!notFound && (
          <Button label="Try again" style={{ marginTop: 16 }} onPress={() => orderQuery.refetch()} />
        )}
        <Button label="Back to Orders" variant="secondary" style={{ marginTop: 12 }} onPress={() => router.back()} />
      </View>
    );
  }

  const isCancelled = order.status === 'CANCELLED';
  const currentIndex = stepIndex(order.status);
  const times = stepTimes(order.events);
  const address = addressLabel(order.deliveryAddress);

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <Text variant="h2" style={styles.headerTitle}>Order Detail</Text>
        <View style={{ width: 36 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* Order summary card */}
        <Card style={styles.summaryCard}>
          <View style={styles.summaryRow}>
            <View style={{ flex: 1 }}>
              <Text variant="label" style={styles.farmName}>
                {orderTitle(order)} {shortId(order.id)}
              </Text>
              <Text variant="caption" style={{ marginTop: 2 }}>
                Placed {formatDateTime(order.createdAt)}
              </Text>
              {order.deliveryDate && (
                <Text variant="caption" style={{ marginTop: 1 }}>
                  Delivery by {formatDate(order.deliveryDate)}
                </Text>
              )}
            </View>
            <Text style={styles.totalAmount}>{rupees(order.total)}</Text>
          </View>

          {/* Items */}
          <View style={styles.itemsList}>
            {order.items.map((item, i) => (
              <View key={`${item.listingId ?? item.name}-${i}`} style={styles.itemRow}>
                <Text variant="caption" style={{ flex: 1, color: '#444' }}>
                  {item.name}  ×{item.qty} {item.unit}
                </Text>
                <Text variant="caption" style={{ color: '#444' }}>
                  {rupees(item.lineTotal)}
                </Text>
              </View>
            ))}
            {order.deliveryFee > 0 && (
              <View style={styles.itemRow}>
                <Text variant="caption" style={{ flex: 1, color: '#444' }}>Delivery</Text>
                <Text variant="caption" style={{ color: '#444' }}>{rupees(order.deliveryFee)}</Text>
              </View>
            )}
            <View style={styles.divider} />
            <View style={styles.itemRow}>
              <Text variant="label" style={{ flex: 1 }}>Total</Text>
              <Text variant="label" style={{ color: '#2d6a4f' }}>{rupees(order.total)}</Text>
            </View>
          </View>

          {/* Delivery address */}
          {address && (
            <View style={styles.addressBox}>
              <Text variant="label">Delivery Address</Text>
              <Text variant="caption" style={{ marginTop: 3, color: '#555' }}>
                📍 {address}
              </Text>
            </View>
          )}
        </Card>

        {/* Timeline */}
        <Text variant="label" style={styles.sectionLabel}>Order Timeline</Text>

        <Card style={styles.timelineCard}>
          {isCancelled ? (
            <CancelledBanner />
          ) : (
            TIMELINE_STEPS.map((step, idx) => (
              <TimelineStep
                key={step}
                stepStatus={step}
                currentIndex={currentIndex}
                stepIndex={idx}
                isLast={idx === TIMELINE_STEPS.length - 1}
                at={times[idx] ?? null}
              />
            ))
          )}
        </Card>

        {/* Issue #8: live position and rating live on their own screen */}
        <Button
          label="Track delivery and rate"
          style={{ marginTop: 8 }}
          onPress={() => router.push({ pathname: '/order-tracking', params: { id: order.id } })}
        />

        <View style={{ height: 32 }} />
      </ScrollView>
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

  scroll: { padding: 16, paddingTop: 12 },

  sectionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#555',
    marginBottom: 8,
    marginTop: 12,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },

  summaryCard: { marginBottom: 4 },
  summaryRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: 10 },
  farmName: { fontSize: 16, fontWeight: '700', color: '#1b1b1b' },
  totalAmount: { fontSize: 20, fontWeight: '700', color: '#2d6a4f' },

  itemsList: {
    backgroundColor: '#fafafa',
    borderRadius: 8,
    padding: 10,
    borderWidth: 1,
    borderColor: '#eee',
    marginBottom: 10,
  },
  itemRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  divider: {
    height: 1,
    backgroundColor: '#eee',
    marginVertical: 6,
  },

  addressBox: {
    backgroundColor: '#f0f7f3',
    borderRadius: 8,
    padding: 10,
    borderWidth: 1,
    borderColor: '#d8ebe0',
  },

  // Timeline
  timelineCard: { paddingVertical: 8 },

  stepRow: {
    flexDirection: 'row',
    minHeight: 60,
  },
  stepLeft: {
    width: 40,
    alignItems: 'center',
  },
  stepDot: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 4,
  },
  stepDotText: { fontSize: 14, color: '#fff', fontWeight: '700' },
  stepDotEmpty: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#ccc',
  },
  stepLine: {
    width: 2,
    flex: 1,
    marginVertical: 2,
  },
  stepContent: {
    flex: 1,
    paddingLeft: 10,
    paddingBottom: 16,
    justifyContent: 'flex-start',
    paddingTop: 8,
  },
  stepFuture: { opacity: 0.4 },
  stepLabel: { fontSize: 14 },

  // Cancelled
  cancelledBanner: {
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#ffebee',
  },
  cancelledEmoji: { fontSize: 36 },
  cancelledTitle: { marginTop: 8, color: '#c62828' },
  cancelledSub: { textAlign: 'center', marginTop: 4, color: '#c62828' },
});
