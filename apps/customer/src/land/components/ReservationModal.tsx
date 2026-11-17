import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  Modal,
  TouchableOpacity,
  ScrollView,
  Text as RNText,
} from 'react-native';
import { Button, Text, Input, Card } from '@farm/ui';
import { formatRupees } from '../../checkout/format.ts';
import { calculateReservationEstimate } from '../filterLogic.ts';
import type { PlotItem, PlotSeasonItem } from '../types.ts';

interface ReservationModalProps {
  visible: boolean;
  plot: PlotItem | null;
  onConfirm: (payload: {
    plot: PlotItem;
    season: PlotSeasonItem;
    cropId: string;
    expectedQtyKg: number;
  }) => void;
  onClose: () => void;
}

export function ReservationModal(props: ReservationModalProps) {
  const { plot } = props;
  return plot ? <ReservationModalBody {...props} plot={plot} /> : null;
}

function ReservationModalBody({
  visible,
  plot,
  onConfirm,
  onClose,
}: Omit<ReservationModalProps, 'plot'> & { plot: PlotItem }) {
  const [selectedSeason, setSelectedSeason] = useState<PlotSeasonItem>(plot.seasons[0]);
  const [selectedCrop, setSelectedCrop] = useState<string>(selectedSeason?.allowedCrops[0] || 'tomato');
  const [qtyKg, setQtyKg] = useState<string>('100');
  const [submitting, setSubmitting] = useState<boolean>(false);

  // Sync when plot changes
  React.useEffect(() => {
    if (plot && plot.seasons.length > 0) {
      setSelectedSeason(plot.seasons[0]);
      setSelectedCrop(plot.seasons[0].allowedCrops[0] || 'tomato');
    }
  }, [plot]);

  const numQty = parseFloat(qtyKg) || 0;
  const { deposit, total } = calculateReservationEstimate(selectedSeason?.pricePerKg || 0, numQty);

  const handleSubmit = async () => {
    if (numQty <= 0) return;
    setSubmitting(true);
    try {
      await onConfirm({
        plot,
        season: selectedSeason,
        cropId: selectedCrop,
        expectedQtyKg: numQty,
      });
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.sheet}>
          <View style={styles.header}>
            <View>
              <Text variant="h2">Reserve Farmland</Text>
              <Text variant="caption">{plot.title} ({plot.areaAcres} Acres)</Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <RNText style={styles.closeBtnText}>✕</RNText>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.content} contentContainerStyle={styles.scrollContent}>
            {/* 1. Season Selection */}
            <View style={styles.section}>
              <Text variant="label" style={styles.sectionTitle}>
                1. Select Agricultural Season
              </Text>
              <View style={styles.seasonOptions}>
                {plot.seasons.map((s) => {
                  const isSelected = selectedSeason.id === s.id;
                  return (
                    <TouchableOpacity
                      key={s.id}
                      style={[styles.seasonCard, isSelected && styles.seasonCardSelected]}
                      onPress={() => {
                        setSelectedSeason(s);
                        if (!s.allowedCrops.includes(selectedCrop)) {
                          setSelectedCrop(s.allowedCrops[0]);
                        }
                      }}
                    >
                      <View style={{ flex: 1 }}>
                        <RNText style={[styles.seasonTitle, isSelected && styles.seasonTitleSelected]}>
                          {s.season}
                        </RNText>
                        <RNText style={styles.seasonDates}>
                          {s.startDate} to {s.endDate}
                        </RNText>
                      </View>
                      <RNText style={[styles.seasonPrice, isSelected && styles.seasonPriceSelected]}>
                        {formatRupees(s.pricePerKg)}/kg
                      </RNText>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* 2. Crop Selection */}
            <View style={styles.section}>
              <Text variant="label" style={styles.sectionTitle}>
                2. Choose Crop to Cultivate
              </Text>
              <View style={styles.cropChips}>
                {selectedSeason.allowedCrops.map((crop) => {
                  const isSelected = selectedCrop === crop;
                  return (
                    <TouchableOpacity
                      key={crop}
                      style={[styles.cropChip, isSelected && styles.cropChipSelected]}
                      onPress={() => setSelectedCrop(crop)}
                    >
                      <RNText
                        style={[styles.cropChipText, isSelected && styles.cropChipTextSelected]}
                      >
                        🌱 {crop.charAt(0).toUpperCase() + crop.slice(1)}
                      </RNText>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* 3. Expected Harvest Quantity */}
            <View style={styles.section}>
              <Text variant="label" style={styles.sectionTitle}>
                3. Expected Target Harvest (kg)
              </Text>
              <Input
                placeholder="Enter kg (e.g. 100, 250)"
                keyboardType="numeric"
                value={qtyKg}
                onChangeText={setQtyKg}
              />
            </View>

            {/* Financial Summary & Deposit Breakdown */}
            <Card style={styles.summaryCard}>
              <Text variant="label" style={styles.summaryTitle}>
                Reservation Agreement Summary
              </Text>

              <View style={styles.summaryRow}>
                <Text variant="caption">Plot Season</Text>
                <Text variant="caption" style={styles.summaryValue}>{selectedSeason.season}</Text>
              </View>

              <View style={styles.summaryRow}>
                <Text variant="caption">Selected Crop</Text>
                <Text variant="caption" style={styles.summaryValue}>
                  {selectedCrop.toUpperCase()}
                </Text>
              </View>

              <View style={styles.summaryRow}>
                <Text variant="caption">Rate per kg</Text>
                <Text variant="caption" style={styles.summaryValue}>
                  {formatRupees(selectedSeason.pricePerKg)}/kg
                </Text>
              </View>

              <View style={styles.divider} />

              <View style={styles.summaryRow}>
                <Text variant="body" style={{ fontWeight: '600' }}>Estimated Total Commitment</Text>
                <Text variant="body" style={{ fontWeight: '700' }}>{formatRupees(total)}</Text>
              </View>

              <View style={styles.summaryRow}>
                <View>
                  <Text variant="label" style={{ color: '#166534' }}>Initial Deposit (20%)</Text>
                  <Text variant="caption" style={{ color: '#166534' }}>Due upon farmer acceptance</Text>
                </View>
                <Text variant="h2" style={{ color: '#166534' }}>{formatRupees(deposit)}</Text>
              </View>
            </Card>

            <View style={styles.termsBox}>
              <RNText style={styles.termsText}>
                ℹ️ The farmer prepares the land, applies organic fertilizer, and posts photo verification updates throughout the growth cycle. At harvest, you can request fresh doorstep delivery or guaranteed buy-back.
              </RNText>
            </View>
          </ScrollView>

          <View style={styles.footer}>
            <Button
              label="Cancel"
              variant="secondary"
              style={{ flex: 1 }}
              onPress={onClose}
              disabled={submitting}
            />
            <Button
              label={submitting ? 'Submitting...' : 'Confirm Reservation'}
              style={{ flex: 1 }}
              onPress={handleSubmit}
              disabled={submitting || numQty <= 0}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: '90%',
    paddingBottom: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  closeBtn: {
    padding: 6,
  },
  closeBtnText: {
    fontSize: 18,
    color: '#888',
    fontWeight: '700',
  },
  content: {
    paddingHorizontal: 20,
  },
  scrollContent: {
    paddingVertical: 14,
    gap: 16,
  },
  section: {
    gap: 8,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#334155',
  },
  seasonOptions: {
    gap: 8,
  },
  seasonCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 12,
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: '#e2e8f0',
    backgroundColor: '#f8fafc',
  },
  seasonCardSelected: {
    borderColor: '#2d6a4f',
    backgroundColor: '#f0fdf4',
  },
  seasonTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#1e293b',
  },
  seasonTitleSelected: {
    color: '#166534',
  },
  seasonDates: {
    fontSize: 11,
    color: '#64748b',
    marginTop: 2,
  },
  seasonPrice: {
    fontSize: 14,
    fontWeight: '700',
    color: '#334155',
  },
  seasonPriceSelected: {
    color: '#166534',
  },
  cropChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  cropChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    backgroundColor: '#fff',
  },
  cropChipSelected: {
    backgroundColor: '#2d6a4f',
    borderColor: '#2d6a4f',
  },
  cropChipText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#334155',
  },
  cropChipTextSelected: {
    color: '#fff',
    fontWeight: '700',
  },
  summaryCard: {
    padding: 14,
    backgroundColor: '#f8fafc',
    borderRadius: 10,
    gap: 8,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  summaryTitle: {
    fontWeight: '700',
    marginBottom: 4,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  summaryValue: {
    fontWeight: '700',
    color: '#1e293b',
  },
  divider: {
    height: 1,
    backgroundColor: '#cbd5e1',
    marginVertical: 4,
  },
  termsBox: {
    backgroundColor: '#f0fdf4',
    padding: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#bbf7d0',
  },
  termsText: {
    fontSize: 11,
    color: '#166534',
    lineHeight: 16,
  },
  footer: {
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
  },
});
