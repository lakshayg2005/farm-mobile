import React from 'react';
import { View, StyleSheet, TouchableOpacity, Text as RNText } from 'react-native';
import { Card, Text, Button } from '@farm/ui';
import { formatRupees } from '../../checkout/format.ts';
import { getStageMetadata } from '../filterLogic.ts';
import type { ReservationItem } from '../types.ts';

interface MyReservationsListProps {
  reservations: ReservationItem[];
  onSelectReservation: (res: ReservationItem) => void;
  onExplorePlots: () => void;
}

export function MyReservationsList({
  reservations,
  onSelectReservation,
  onExplorePlots,
}: MyReservationsListProps) {
  if (reservations.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <RNText style={styles.emptyIcon}>🌾</RNText>
        <Text variant="h2">No Active Reservations</Text>
        <Text variant="caption" style={styles.emptyText}>
          You haven't reserved any farmland plots yet. Browse nearby plots to reserve land and track your own organic crop!
        </Text>
        <Button label="Browse Farmland Plots" onPress={onExplorePlots} style={{ marginTop: 14 }} />
      </View>
    );
  }

  return (
    <View style={styles.list}>
      {reservations.map((res) => {
        const latestUpdate = res.updates[res.updates.length - 1];
        const stageMeta = getStageMetadata(latestUpdate?.stage || 'SOWN');

        return (
          <TouchableOpacity
            key={res.id}
            activeOpacity={0.9}
            onPress={() => onSelectReservation(res)}
          >
            <Card style={styles.card}>
              <View style={styles.topRow}>
                <View style={{ flex: 1 }}>
                  <Text variant="h2" style={styles.cropTitle}>
                    {res.cropName}
                  </Text>
                  <Text variant="caption" style={styles.plotTitle}>
                    🏡 {res.plotTitle} ({res.seasonName})
                  </Text>
                </View>

                <View
                  style={[
                    styles.statusBadge,
                    res.status === 'HARVEST_READY'
                      ? styles.statusHarvest
                      : res.status === 'ACTIVE'
                      ? styles.statusActive
                      : styles.statusPending,
                  ]}
                >
                  <RNText
                    style={[
                      styles.statusText,
                      res.status === 'HARVEST_READY'
                        ? styles.statusHarvestText
                        : res.status === 'ACTIVE'
                        ? styles.statusActiveText
                        : styles.statusPendingText,
                    ]}
                  >
                    {res.status.replace(/_/g, ' ')}
                  </RNText>
                </View>
              </View>

              {/* Progress Summary Pill */}
              <View style={[styles.progressBox, { backgroundColor: stageMeta.bgColor }]}>
                <RNText style={styles.progressIcon}>{stageMeta.icon}</RNText>
                <View style={{ flex: 1 }}>
                  <RNText style={[styles.progressStage, { color: stageMeta.color }]}>
                    Current Stage: {latestUpdate?.stageTitle || stageMeta.label}
                  </RNText>
                  <RNText style={styles.progressNote} numberOfLines={1}>
                    {latestUpdate?.note || 'Field preparation underway'}
                  </RNText>
                </View>
              </View>

              <View style={styles.footerRow}>
                <View>
                  <Text variant="caption">Target Harvest</Text>
                  <Text variant="label">{res.expectedQtyKg} kg</Text>
                </View>
                <View>
                  <Text variant="caption">Deposit Paid</Text>
                  <Text variant="label">{formatRupees(res.deposit)}</Text>
                </View>

                <View style={styles.actionPill}>
                  <RNText style={styles.actionPillText}>Track Timeline →</RNText>
                </View>
              </View>
            </Card>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  list: {
    gap: 12,
  },
  card: {
    padding: 16,
    borderRadius: 14,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    gap: 10,
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  cropTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: '#0f172a',
  },
  plotTitle: {
    color: '#475569',
    marginTop: 2,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  statusActive: {
    backgroundColor: '#dcfce7',
  },
  statusActiveText: {
    color: '#15803d',
    fontWeight: '700',
    fontSize: 11,
  },
  statusHarvest: {
    backgroundColor: '#f3e8ff',
  },
  statusHarvestText: {
    color: '#7e22ce',
    fontWeight: '800',
    fontSize: 11,
  },
  statusPending: {
    backgroundColor: '#fef3c7',
  },
  statusPendingText: {
    color: '#b45309',
    fontWeight: '700',
    fontSize: 11,
  },
  statusText: {
    fontSize: 11,
  },
  progressBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 10,
    borderRadius: 8,
  },
  progressIcon: {
    fontSize: 16,
  },
  progressStage: {
    fontSize: 12,
    fontWeight: '700',
  },
  progressNote: {
    fontSize: 11,
    color: '#475569',
    marginTop: 1,
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
  },
  actionPill: {
    backgroundColor: '#0f766e',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  actionPillText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 12,
  },
  emptyContainer: {
    padding: 32,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 8,
  },
  emptyText: {
    textAlign: 'center',
    color: '#64748b',
    marginTop: 6,
    lineHeight: 18,
  },
});
