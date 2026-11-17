import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity, Text as RNText } from 'react-native';
import { Button, Card, Text } from '@farm/ui';
import { formatDistance } from '../../browse/format.ts';
import { formatRupees } from '../../checkout/format.ts';
import type { PlotItem, LocationCoords } from '../types.ts';

interface PlotMapViewProps {
  plots: PlotItem[];
  userLocation: LocationCoords;
  onReserve: (plot: PlotItem) => void;
}

export function PlotMapView({ plots, userLocation, onReserve }: PlotMapViewProps) {
  const [selectedPlotId, setSelectedPlotId] = useState<string | null>(plots[0]?.id ?? null);
  const selectedPlot = plots.find((p) => p.id === selectedPlotId) || plots[0];

  return (
    <View style={styles.container}>
      <View style={styles.mapCanvas}>
        <View style={styles.mapGridPattern}>
          {/* Customer Location */}
          <View style={styles.centerMarker}>
            <View style={styles.pulseRing} />
            <View style={styles.userPin}>
              <RNText style={styles.userPinIcon}>📍</RNText>
            </View>
            <View style={styles.userLabelCard}>
              <RNText style={styles.userLabelText}>You ({userLocation.name})</RNText>
            </View>
          </View>

          {/* Plot Markers */}
          {plots.map((plot, idx) => {
            const isSelected = plot.id === selectedPlot?.id;
            const angles = [45, 135, 225, 315, 90, 180, 270];
            const distRadius = [80, 120, 100, 130, 95, 115, 135];
            const angle = angles[idx % angles.length];
            const r = distRadius[idx % distRadius.length];
            const rad = (angle * Math.PI) / 180;
            const offsetX = Math.cos(rad) * r;
            const offsetY = Math.sin(rad) * r;

            return (
              <TouchableOpacity
                key={plot.id}
                activeOpacity={0.8}
                onPress={() => setSelectedPlotId(plot.id)}
                style={[
                  styles.plotPin,
                  { transform: [{ translateX: offsetX }, { translateY: offsetY }] },
                ]}
              >
                <View style={[styles.pinBubble, isSelected && styles.pinBubbleSelected]}>
                  <RNText style={styles.pinIcon}>🌾</RNText>
                  <RNText style={[styles.pinText, isSelected && styles.pinTextSelected]}>
                    {plot.areaAcres} ac
                  </RNText>
                </View>
                <View style={[styles.pinPointer, isSelected && styles.pinPointerSelected]} />
              </TouchableOpacity>
            );
          })}
        </View>

        {/* Map Header Status Banner */}
        <View style={styles.mapStatusBanner}>
          <RNText style={styles.mapStatusText}>
            🗺 Showing {plots.length} farm plots near {userLocation.name}
          </RNText>
        </View>
      </View>

      {/* Selected Plot Bottom Preview Card */}
      {selectedPlot && (
        <View style={styles.previewContainer}>
          <Card style={styles.previewCard}>
            <View style={styles.previewHeader}>
              <View style={{ flex: 1 }}>
                <Text variant="h2" style={styles.previewTitle}>
                  {selectedPlot.title}
                </Text>
                <Text variant="caption" style={styles.previewLocation}>
                  🏡 {selectedPlot.village}, {selectedPlot.district} · {formatDistance(selectedPlot.distanceM)} away
                </Text>
              </View>

              <View style={styles.acreBadge}>
                <RNText style={styles.acreText}>{selectedPlot.areaAcres} Acres</RNText>
              </View>
            </View>

            <View style={styles.specsRow}>
              <RNText style={styles.specText}>🌱 {selectedPlot.soilType}</RNText>
              <RNText style={styles.specText}>💧 {selectedPlot.waterSource}</RNText>
            </View>

            <View style={styles.seasonsPreview}>
              <RNText style={styles.seasonsLabel}>
                Seasons: {selectedPlot.seasons.map((s) => `${s.season} (${formatRupees(s.pricePerKg)}/kg)`).join(' · ')}
              </RNText>
            </View>

            <View style={styles.previewActions}>
              <View>
                <Text variant="caption">Starting from</Text>
                <Text variant="h2" style={styles.priceHighlight}>
                  {formatRupees(Math.min(...selectedPlot.seasons.map((s) => s.pricePerKg), 0))}/kg
                </Text>
              </View>

              <Button
                label="Reserve This Plot →"
                onPress={() => onReserve(selectedPlot)}
                style={styles.reserveBtn}
              />
            </View>
          </Card>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    height: 480,
    borderRadius: 14,
    overflow: 'hidden',
    backgroundColor: '#f1f5f9',
    position: 'relative',
    marginVertical: 10,
    borderWidth: 1,
    borderColor: '#cbd5e1',
  },
  mapCanvas: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#e2e8f0',
  },
  mapGridPattern: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  mapStatusBanner: {
    position: 'absolute',
    top: 10,
    backgroundColor: 'rgba(255, 255, 255, 0.94)',
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#cbd5e1',
  },
  mapStatusText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#0f766e',
  },
  centerMarker: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 10,
  },
  pulseRing: {
    position: 'absolute',
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'rgba(15, 118, 110, 0.2)',
  },
  userPin: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#0f766e',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#fff',
  },
  userPinIcon: {
    fontSize: 14,
  },
  userLabelCard: {
    backgroundColor: '#134e4a',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
    marginTop: 4,
  },
  userLabelText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: '700',
  },
  plotPin: {
    position: 'absolute',
    alignItems: 'center',
    zIndex: 5,
  },
  pinBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: '#fff',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: '#2d6a4f',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 3,
    elevation: 3,
  },
  pinBubbleSelected: {
    backgroundColor: '#2d6a4f',
    borderColor: '#1b4332',
    transform: [{ scale: 1.1 }],
  },
  pinIcon: {
    fontSize: 12,
  },
  pinText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#1b1b1b',
  },
  pinTextSelected: {
    color: '#fff',
  },
  pinPointer: {
    width: 6,
    height: 6,
    backgroundColor: '#2d6a4f',
    transform: [{ rotate: '45deg' }],
    marginTop: -3,
  },
  pinPointerSelected: {
    backgroundColor: '#1b4332',
  },
  previewContainer: {
    position: 'absolute',
    bottom: 10,
    left: 10,
    right: 10,
    zIndex: 20,
  },
  previewCard: {
    padding: 14,
    backgroundColor: '#fff',
    borderRadius: 14,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 5,
    gap: 8,
  },
  previewHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  previewTitle: {
    fontSize: 17,
    fontWeight: '700',
  },
  previewLocation: {
    color: '#475569',
    marginTop: 2,
  },
  acreBadge: {
    backgroundColor: '#f0fdf4',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#bbf7d0',
  },
  acreText: {
    color: '#166534',
    fontSize: 11,
    fontWeight: '700',
  },
  specsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  specText: {
    fontSize: 11,
    color: '#64748b',
    fontWeight: '600',
  },
  seasonsPreview: {
    backgroundColor: '#f8fafc',
    padding: 6,
    borderRadius: 6,
  },
  seasonsLabel: {
    fontSize: 11,
    color: '#334155',
  },
  previewActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 4,
  },
  priceHighlight: {
    fontSize: 18,
    fontWeight: '800',
    color: '#2d6a4f',
  },
  reserveBtn: {
    paddingHorizontal: 16,
  },
});
