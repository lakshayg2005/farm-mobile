import React, { useState } from 'react';
import {
  Modal,
  View,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  TextInput,
} from 'react-native';
import { Button, Text, Card } from '@farm/ui';
import { submitPickup } from '@farm/api';

export interface PickupStop {
  id: string;
  label: string;
  address: string;
  customerName: string;
  items: string[];
}

interface PickupModalProps {
  visible: boolean;
  stop: PickupStop | null;
  onClose: () => void;
  onSuccess: (stopId: string) => void;
}

export function PickupModal({ visible, stop, onClose, onSuccess }: PickupModalProps) {
  const [checkedItems, setCheckedItems] = useState<Record<number, boolean>>({});
  const [notes, setNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!stop) return null;

  const toggleItem = (idx: number) => {
    setCheckedItems(prev => ({ ...prev, [idx]: !prev[idx] }));
  };

  const allItemsChecked =
    stop.items.length === 0 || stop.items.every((_, idx) => Boolean(checkedItems[idx]));

  const handleConfirmPickup = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);

    try {
      await submitPickup({
        stopId: stop.id,
        notes: notes.trim() || undefined,
      });

      onSuccess(stop.id);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Pickup confirmation failed';
      setErrorMsg(msg);
      setIsSubmitting(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.sheetContainer}>
          <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
            {/* Header */}
            <View style={styles.headerRow}>
              <View>
                <Text variant="h2" style={styles.headerTitle}>
                  Confirm Farm Pickup
                </Text>
                <Text variant="caption" style={styles.headerSubtitle}>
                  {stop.customerName} · {stop.address}
                </Text>
              </View>
              <TouchableOpacity onPress={onClose} disabled={isSubmitting} style={styles.closeBtn}>
                <Text style={styles.closeIcon}>✕</Text>
              </TouchableOpacity>
            </View>

            {errorMsg && (
              <View style={styles.errorBanner}>
                <Text style={styles.errorText}>⚠️ {errorMsg}</Text>
              </View>
            )}

            {/* Checklist Card */}
            <Card style={styles.sectionCard}>
              <Text variant="label" style={styles.sectionTitle}>
                Verify Items with Farmer ({stop.items.length} items)
              </Text>
              <Text variant="caption" style={styles.sectionSubtitle}>
                Tap each item to check it off as you load it:
              </Text>

              {stop.items.map((item, idx) => {
                const isChecked = Boolean(checkedItems[idx]);
                return (
                  <TouchableOpacity
                    key={idx}
                    style={[styles.itemRow, isChecked && styles.itemRowChecked]}
                    onPress={() => toggleItem(idx)}
                    disabled={isSubmitting || !allItemsChecked}
                  >
                    <View style={[styles.checkbox, isChecked && styles.checkboxChecked]}>
                      <Text style={styles.checkboxText}>{isChecked ? '✓' : ''}</Text>
                    </View>
                    <Text
                      variant="body"
                      style={[styles.itemText, isChecked && styles.itemTextChecked]}
                    >
                      {item}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </Card>

            {/* Optional notes */}
            <Card style={styles.sectionCard}>
              <Text variant="label" style={styles.sectionTitle}>
                Farmer / Quality Notes (Optional)
              </Text>
              <TextInput
                style={styles.notesInput}
                placeholder="e.g. All crates in good condition, fresh harvest"
                placeholderTextColor="#999"
                value={notes}
                onChangeText={setNotes}
                editable={!isSubmitting}
                multiline
                numberOfLines={2}
              />
            </Card>

            {/* Actions */}
            <View style={styles.footerActions}>
              <Button
                label={isSubmitting ? 'Confirming Pickup...' : '📦 Confirm All Items Picked Up'}
                onPress={handleConfirmPickup}
                disabled={isSubmitting || !allItemsChecked}
                loading={isSubmitting}
                style={styles.submitBtn}
              />
              <Button
                label="Cancel"
                variant="ghost"
                onPress={onClose}
                disabled={isSubmitting || !allItemsChecked}
                style={{ marginTop: 8 }}
              />
            </View>
          </ScrollView>
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
  sheetContainer: {
    backgroundColor: '#f5f5f0',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: '85%',
  },
  scrollContent: {
    padding: 18,
    paddingBottom: 36,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1b1b1b',
  },
  headerSubtitle: {
    marginTop: 2,
    color: '#666',
  },
  closeBtn: {
    padding: 6,
    borderRadius: 16,
    backgroundColor: '#e0e0da',
  },
  closeIcon: {
    fontSize: 16,
    color: '#444',
  },
  errorBanner: {
    backgroundColor: '#ffebee',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
  },
  errorText: {
    color: '#c62828',
    fontSize: 13,
  },
  sectionCard: {
    marginBottom: 14,
    padding: 14,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1b1b1b',
  },
  sectionSubtitle: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
    marginBottom: 10,
  },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 8,
    borderRadius: 8,
    marginVertical: 2,
  },
  itemRowChecked: {
    backgroundColor: '#e8f5e9',
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#999',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
    backgroundColor: '#fff',
  },
  checkboxChecked: {
    backgroundColor: '#2d6a4f',
    borderColor: '#2d6a4f',
  },
  checkboxText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 14,
  },
  itemText: {
    fontSize: 14,
    color: '#333',
    flex: 1,
  },
  itemTextChecked: {
    color: '#1b5e20',
    fontWeight: '500',
  },
  notesInput: {
    backgroundColor: '#fafafa',
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 10,
    fontSize: 14,
    color: '#333',
    minHeight: 50,
  },
  footerActions: {
    marginTop: 8,
  },
  submitBtn: {
    paddingVertical: 14,
  },
});
