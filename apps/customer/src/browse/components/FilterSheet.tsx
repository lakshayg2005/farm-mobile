import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  Modal,
  TouchableOpacity,
  ScrollView,
  Text as RNText,
} from 'react-native';
import { Button, Text, Input } from '@farm/ui';
import type { FilterState, SortOption, Grade } from '../types.ts';

interface FilterSheetProps {
  visible: boolean;
  filters: FilterState;
  onApply: (updated: FilterState) => void;
  onReset: () => void;
  onClose: () => void;
}

const RADIUS_OPTIONS = [5, 10, 25, 50];
const GRADE_OPTIONS: Array<Grade | 'ALL'> = ['ALL', 'A', 'B', 'C'];
const SORT_OPTIONS: Array<{ key: SortOption; label: string }> = [
  { key: 'distance', label: 'Nearest' },
  { key: 'price_asc', label: 'Price: Low to High' },
  { key: 'price_desc', label: 'Price: High to Low' },
  { key: 'harvest_date', label: 'Freshest Harvest' },
];

export function FilterSheet({ visible, filters, onApply, onReset, onClose }: FilterSheetProps) {
  const [draft, setDraft] = useState<FilterState>(filters);

  // Sync draft when opened
  React.useEffect(() => {
    setDraft(filters);
  }, [visible, filters]);

  const handleApply = () => {
    onApply(draft);
    onClose();
  };

  const handleReset = () => {
    onReset();
    onClose();
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.sheet}>
          <View style={styles.header}>
            <Text variant="h2">Filters & Sort</Text>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <RNText style={styles.closeBtnText}>✕</RNText>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.content} contentContainerStyle={styles.scrollContent}>
            {/* Search Radius */}
            <View style={styles.section}>
              <Text variant="label" style={styles.sectionTitle}>
                Search Radius
              </Text>
              <View style={styles.chipRow}>
                {RADIUS_OPTIONS.map((r) => (
                  <TouchableOpacity
                    key={r}
                    style={[styles.chip, draft.radiusKm === r && styles.chipActive]}
                    onPress={() => setDraft((prev) => ({ ...prev, radiusKm: r }))}
                  >
                    <RNText style={[styles.chipText, draft.radiusKm === r && styles.chipTextActive]}>
                      {r} km
                    </RNText>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Sort Options */}
            <View style={styles.section}>
              <Text variant="label" style={styles.sectionTitle}>
                Sort By
              </Text>
              <View style={styles.chipRow}>
                {SORT_OPTIONS.map((opt) => (
                  <TouchableOpacity
                    key={opt.key}
                    style={[styles.chip, draft.sort === opt.key && styles.chipActive]}
                    onPress={() => setDraft((prev) => ({ ...prev, sort: opt.key }))}
                  >
                    <RNText
                      style={[styles.chipText, draft.sort === opt.key && styles.chipTextActive]}
                    >
                      {opt.label}
                    </RNText>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Quality Grade */}
            <View style={styles.section}>
              <Text variant="label" style={styles.sectionTitle}>
                Minimum Quality Grade
              </Text>
              <View style={styles.chipRow}>
                {GRADE_OPTIONS.map((g) => (
                  <TouchableOpacity
                    key={g}
                    style={[styles.chip, draft.minGrade === g && styles.chipActive]}
                    onPress={() => setDraft((prev) => ({ ...prev, minGrade: g }))}
                  >
                    <RNText
                      style={[styles.chipText, draft.minGrade === g && styles.chipTextActive]}
                    >
                      {g === 'ALL' ? 'Any Grade' : `Grade ${g} & above`}
                    </RNText>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Organic Only */}
            <View style={styles.section}>
              <TouchableOpacity
                style={styles.toggleRow}
                activeOpacity={0.8}
                onPress={() =>
                  setDraft((prev) => ({ ...prev, organicOnly: !prev.organicOnly }))
                }
              >
                <View>
                  <Text variant="label">🌱 Organic Certified Only</Text>
                  <Text variant="caption" style={{ marginTop: 2 }}>
                    Only show listings grown with natural organic methods
                  </Text>
                </View>
                <View
                  style={[
                    styles.checkbox,
                    draft.organicOnly && styles.checkboxActive,
                  ]}
                >
                  {draft.organicOnly && <RNText style={styles.checkmark}>✓</RNText>}
                </View>
              </TouchableOpacity>
            </View>

            {/* Price Range */}
            <View style={styles.section}>
              <Text variant="label" style={styles.sectionTitle}>
                Price Range (₹ per unit)
              </Text>
              <View style={styles.priceRow}>
                <View style={{ flex: 1 }}>
                  <Input
                    placeholder="Min (e.g. 10)"
                    keyboardType="numeric"
                    value={draft.minPrice ? String(draft.minPrice) : ''}
                    onChangeText={(val) =>
                      setDraft((prev) => ({
                        ...prev,
                        minPrice: val ? parseFloat(val) : null,
                      }))
                    }
                  />
                </View>
                <RNText style={styles.priceDivider}>to</RNText>
                <View style={{ flex: 1 }}>
                  <Input
                    placeholder="Max (e.g. 100)"
                    keyboardType="numeric"
                    value={draft.maxPrice ? String(draft.maxPrice) : ''}
                    onChangeText={(val) =>
                      setDraft((prev) => ({
                        ...prev,
                        maxPrice: val ? parseFloat(val) : null,
                      }))
                    }
                  />
                </View>
              </View>
            </View>
          </ScrollView>

          <View style={styles.footer}>
            <Button
              label="Reset"
              variant="secondary"
              style={{ flex: 1 }}
              onPress={handleReset}
            />
            <Button
              label="Apply Filters"
              style={{ flex: 1 }}
              onPress={handleApply}
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
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: '85%',
    paddingBottom: 24,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0eb',
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
    paddingVertical: 12,
    gap: 18,
  },
  section: {
    gap: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: '#f5f5f0',
    borderWidth: 1,
    borderColor: '#e8e8e3',
  },
  chipActive: {
    backgroundColor: '#2d6a4f',
    borderColor: '#2d6a4f',
  },
  chipText: {
    fontSize: 13,
    color: '#444',
    fontWeight: '500',
  },
  chipTextActive: {
    color: '#fff',
    fontWeight: '700',
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#cbd5e1',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxActive: {
    backgroundColor: '#2d6a4f',
    borderColor: '#2d6a4f',
  },
  checkmark: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  priceDivider: {
    fontSize: 14,
    color: '#888',
  },
  footer: {
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#f0f0eb',
  },
});
