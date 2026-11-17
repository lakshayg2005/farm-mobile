import React from 'react';
import { View, StyleSheet, TouchableOpacity, Text as RNText } from 'react-native';
import { Card, Text } from '@farm/ui';
import { formatDistance } from '../../browse/format.ts';
import { formatRupees } from '../../checkout/format.ts';
import type { PlotItem } from '../types.ts';

interface PlotCardProps {
  plot: PlotItem;
  onReserve: (plot: PlotItem) => void;
  onViewDetails?: (plot: PlotItem) => void;
}

export function PlotCard({ plot, onReserve }: PlotCardProps) {
  return (
    <Card style={styles.card}>
      <View style={styles.topRow}>
        <View style={styles.titleArea}>
          <Text variant="h2" style={styles.plotTitle}>
            {plot.title}
          </Text>
          <View style={styles.locationRow}>
            <RNText style={styles.locIcon}>📍</RNText>
            <Text variant="caption" style={styles.locText}>
              {plot.village}, {plot.district} ({formatDistance(plot.distanceM)} away)
            </Text>
          </View>
        </View>

        <View style={styles.areaBadge}>
          <RNText style={styles.areaNumber}>{plot.areaAcres}</RNText>
          <RNText style={styles.areaUnit}>Acres</RNText>
        </View>
      </View>

      {/* Soil & Water Badges */}
      <View style={styles.specsRow}>
        <View style={styles.specChip}>
          <RNText style={styles.specLabel}>🌱 Soil: {plot.soilType}</RNText>
        </View>
        <View style={styles.specChip}>
          <RNText style={styles.specLabel}>💧 {plot.waterSource}</RNText>
        </View>
      </View>

      {plot.description ? (
        <Text variant="body" style={styles.description} numberOfLines={2}>
          {plot.description}
        </Text>
      ) : null}

      {/* Available Seasons & Crops */}
      <View style={styles.seasonsBox}>
        <Text variant="label" style={styles.seasonsTitle}>
          Available Seasons ({plot.seasons.length})
        </Text>
        {plot.seasons.map((s) => (
          <View key={s.id} style={styles.seasonRow}>
            <View style={{ flex: 1 }}>
              <RNText style={styles.seasonName}>🗓 {s.season}</RNText>
              <RNText style={styles.cropsList}>
                Crops: {s.allowedCrops.join(', ')}
              </RNText>
            </View>
            <RNText style={styles.seasonPrice}>
              {formatRupees(s.pricePerKg)}/kg
            </RNText>
          </View>
        ))}
      </View>

      {/* Farmer & Action Row */}
      <View style={styles.footerRow}>
        <View>
          <Text variant="caption">Farmer Partner</Text>
          <Text variant="label" style={styles.farmerName}>
            👨‍🌾 {plot.farmerName}
          </Text>
        </View>

        <TouchableOpacity
          style={styles.reserveBtn}
          activeOpacity={0.8}
          onPress={() => onReserve(plot)}
          accessibilityLabel={`Reserve plot ${plot.title}`}
        >
          <RNText style={styles.reserveBtnText}>Reserve Plot →</RNText>
        </TouchableOpacity>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: 16,
    borderRadius: 14,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e5e7eb',
    marginBottom: 14,
    gap: 10,
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  titleArea: {
    flex: 1,
    paddingRight: 8,
  },
  plotTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1b1b1b',
  },
  locationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: 2,
  },
  locIcon: {
    fontSize: 12,
  },
  locText: {
    color: '#4b5563',
  },
  areaBadge: {
    backgroundColor: '#f0fdf4',
    borderWidth: 1.5,
    borderColor: '#86efac',
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 4,
    alignItems: 'center',
  },
  areaNumber: {
    fontSize: 18,
    fontWeight: '900',
    color: '#166534',
  },
  areaUnit: {
    fontSize: 10,
    fontWeight: '700',
    color: '#166534',
    textTransform: 'uppercase',
  },
  specsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  specChip: {
    backgroundColor: '#f8fafc',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  specLabel: {
    fontSize: 11,
    color: '#475569',
    fontWeight: '600',
  },
  description: {
    color: '#4b5563',
    fontSize: 13,
    lineHeight: 18,
  },
  seasonsBox: {
    backgroundColor: '#f9fafb',
    borderRadius: 8,
    padding: 10,
    gap: 6,
    borderWidth: 1,
    borderColor: '#f1f5f9',
  },
  seasonsTitle: {
    fontSize: 11,
    fontWeight: '700',
    color: '#64748b',
    textTransform: 'uppercase',
  },
  seasonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 2,
  },
  seasonName: {
    fontSize: 12,
    fontWeight: '700',
    color: '#1f2937',
  },
  cropsList: {
    fontSize: 11,
    color: '#6b7280',
    marginTop: 1,
  },
  seasonPrice: {
    fontSize: 13,
    fontWeight: '700',
    color: '#2d6a4f',
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 4,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
  },
  farmerName: {
    color: '#2d6a4f',
    fontWeight: '700',
    fontSize: 13,
  },
  reserveBtn: {
    backgroundColor: '#2d6a4f',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
  },
  reserveBtnText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 13,
  },
});
