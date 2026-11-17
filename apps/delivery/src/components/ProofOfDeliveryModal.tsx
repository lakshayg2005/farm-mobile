import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  StyleSheet,
  TouchableOpacity,
  Image,
  TextInput,
  ScrollView,
  Alert,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { Button, Text, Card } from '@farm/ui';
import { uploadDeliveryPhoto, submitDelivery } from '@farm/api';

export interface ProofOfDeliveryStop {
  id: string;
  label: string;
  address: string;
  customerName: string;
  items: string[];
  otp?: string;
}

interface ProofOfDeliveryModalProps {
  visible: boolean;
  stop: ProofOfDeliveryStop | null;
  onClose: () => void;
  onSuccess: (stopId: string) => void;
}

export function ProofOfDeliveryModal({
  visible,
  stop,
  onClose,
  onSuccess,
}: ProofOfDeliveryModalProps) {
  const [photoUri, setPhotoUri] = useState<string | null>(null);
  const [otpInput, setOtpInput] = useState('');
  const [notes, setNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Reset state when opening with a new stop
  useEffect(() => {
    if (visible) {
      setPhotoUri(null);
      setOtpInput('');
      setNotes('');
      setErrorMsg(null);
      setIsSubmitting(false);
    }
  }, [visible, stop?.id]);

  if (!stop) return null;

  const expectedOtp = stop.otp;
  const isOtpComplete = otpInput.length === 4;
  const isOtpValid = expectedOtp ? otpInput === expectedOtp : isOtpComplete;
  const canSubmit = Boolean(photoUri && isOtpValid && !isSubmitting);

  const handleTakePhoto = async () => {
    try {
      const permission = await ImagePicker.requestCameraPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Camera Permission Required', 'Please enable camera access to take proof of delivery.');
        return;
      }

      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        allowsEditing: true,
        aspect: [4, 3],
        quality: 0.7,
      });

      if (!result.canceled && result.assets[0]?.uri) {
        setPhotoUri(result.assets[0].uri);
        setErrorMsg(null);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to launch camera';
      setErrorMsg(msg);
    }
  };

  const handlePickFromGallery = async () => {
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Permission Required', 'Please enable gallery access to choose a photo.');
        return;
      }

      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        allowsEditing: true,
        aspect: [4, 3],
        quality: 0.7,
      });

      if (!result.canceled && result.assets[0]?.uri) {
        setPhotoUri(result.assets[0].uri);
        setErrorMsg(null);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to pick image';
      setErrorMsg(msg);
    }
  };

  const handleConfirmDelivery = async () => {
    if (!photoUri || !isOtpValid) return;

    setIsSubmitting(true);
    setErrorMsg(null);

    try {
      // 1. Upload proof photo via media service
      const uploadRes = await uploadDeliveryPhoto({
        uri: photoUri,
        stopId: stop.id,
      });

      // 2. Submit delivery confirmation to logistics service
      await submitDelivery({
        stopId: stop.id,
        otp: otpInput,
        photoMediaId: uploadRes.mediaId,
        notes: notes.trim() || undefined,
      });

      onSuccess(stop.id);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Delivery submission failed';
      setErrorMsg(msg);
      setIsSubmitting(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.sheetContainer}>
          <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
            {/* Sheet Header */}
            <View style={styles.headerRow}>
              <View>
                <Text variant="h2" style={styles.headerTitle}>
                  Proof of Delivery
                </Text>
                <Text variant="caption" style={styles.headerSubtitle}>
                  {stop.customerName} · {stop.address}
                </Text>
              </View>
              <TouchableOpacity onPress={onClose} disabled={isSubmitting} style={styles.closeBtn}>
                <Text style={styles.closeIcon}>✕</Text>
              </TouchableOpacity>
            </View>

            {/* Error Message */}
            {errorMsg && (
              <View style={styles.errorBanner}>
                <Text style={styles.errorText}>⚠️ {errorMsg}</Text>
              </View>
            )}

            {/* 1. Photo Capture Section */}
            <Card style={styles.sectionCard}>
              <Text variant="label" style={styles.sectionTitle}>
                1. Delivery Photo Proof (Required)
              </Text>
              <Text variant="caption" style={styles.sectionSubtitle}>
                Take a clear photo of the delivered package at customer location.
              </Text>

              {photoUri ? (
                <View style={styles.previewContainer}>
                  <Image source={{ uri: photoUri }} style={styles.previewImage} />
                  <View style={styles.previewActions}>
                    <TouchableOpacity
                      style={styles.retakeBtn}
                      onPress={handleTakePhoto}
                      disabled={isSubmitting}
                    >
                      <Text style={styles.retakeText}>📷 Retake Photo</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.retakeBtn}
                      onPress={handlePickFromGallery}
                      disabled={isSubmitting}
                    >
                      <Text style={styles.retakeText}>🖼️ Choose Different</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              ) : (
                <View style={styles.photoActionsRow}>
                  <TouchableOpacity
                    style={[styles.photoButton, styles.cameraBtn]}
                    onPress={handleTakePhoto}
                    disabled={isSubmitting}
                  >
                    <Text style={styles.photoBtnIcon}>📷</Text>
                    <Text style={styles.cameraBtnText}>Take Photo</Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={[styles.photoButton, styles.galleryBtn]}
                    onPress={handlePickFromGallery}
                    disabled={isSubmitting}
                  >
                    <Text style={styles.photoBtnIcon}>🖼️</Text>
                    <Text style={styles.galleryBtnText}>Gallery</Text>
                  </TouchableOpacity>
                </View>
              )}
            </Card>

            {/* 2. Customer OTP Verification Section */}
            <Card style={styles.sectionCard}>
              <Text variant="label" style={styles.sectionTitle}>
                2. Customer OTP Verification (Required)
              </Text>
              <Text variant="caption" style={styles.sectionSubtitle}>
                Ask the customer for the 4-digit code shown on their order screen.
              </Text>

              <View style={styles.otpInputWrapper}>
                <TextInput
                  style={[
                    styles.otpInput,
                    isOtpComplete && (isOtpValid ? styles.otpInputSuccess : styles.otpInputError),
                  ]}
                  value={otpInput}
                  onChangeText={text => setOtpInput(text.replace(/[^0-9]/g, '').slice(0, 4))}
                  placeholder="0 0 0 0"
                  placeholderTextColor="#999"
                  keyboardType="number-pad"
                  maxLength={4}
                  editable={!isSubmitting}
                />
              </View>

              {/* OTP Validation Status */}
              {isOtpComplete && (
                <View style={styles.otpStatusRow}>
                  {isOtpValid ? (
                    <Text style={styles.otpSuccessText}>✓ OTP matches! Delivery verified.</Text>
                  ) : (
                    <Text style={styles.otpErrorText}>
                      ✗ Invalid OTP. Please confirm the 4-digit code with customer.
                    </Text>
                  )}
                </View>
              )}
            </Card>

            {/* 3. Delivery Notes (Optional) */}
            <Card style={styles.sectionCard}>
              <Text variant="label" style={styles.sectionTitle}>
                3. Delivery Note (Optional)
              </Text>
              <TextInput
                style={styles.notesInput}
                placeholder="e.g. Handed to customer, placed on doorstep"
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
                label={isSubmitting ? 'Submitting Delivery...' : '✅ Complete Delivery'}
                onPress={handleConfirmDelivery}
                disabled={!canSubmit}
                loading={isSubmitting}
                style={StyleSheet.flatten([styles.submitBtn, !canSubmit && styles.submitBtnDisabled])}
              />
              <Button
                label="Cancel"
                variant="ghost"
                onPress={onClose}
                disabled={isSubmitting}
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
    maxHeight: '90%',
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
    marginBottom: 12,
  },
  photoActionsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  photoButton: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cameraBtn: {
    backgroundColor: '#2d6a4f',
  },
  galleryBtn: {
    backgroundColor: '#fff',
    borderWidth: 1.5,
    borderColor: '#2d6a4f',
  },
  photoBtnIcon: {
    fontSize: 22,
    marginBottom: 4,
  },
  cameraBtnText: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 14,
  },
  galleryBtnText: {
    color: '#2d6a4f',
    fontWeight: '600',
    fontSize: 14,
  },
  previewContainer: {
    alignItems: 'center',
  },
  previewImage: {
    width: '100%',
    height: 180,
    borderRadius: 10,
    backgroundColor: '#e0e0e0',
  },
  previewActions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 10,
  },
  retakeBtn: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 6,
    backgroundColor: '#e8f5e9',
  },
  retakeText: {
    color: '#2d6a4f',
    fontSize: 13,
    fontWeight: '500',
  },
  otpInputWrapper: {
    alignItems: 'center',
    marginVertical: 6,
  },
  otpInput: {
    width: 180,
    height: 52,
    borderWidth: 2,
    borderColor: '#ccc',
    borderRadius: 10,
    backgroundColor: '#fff',
    fontSize: 26,
    letterSpacing: 10,
    textAlign: 'center',
    fontWeight: '700',
    color: '#1b1b1b',
  },
  otpInputSuccess: {
    borderColor: '#2d6a4f',
    backgroundColor: '#f1f8e9',
  },
  otpInputError: {
    borderColor: '#c62828',
    backgroundColor: '#ffebee',
  },
  otpStatusRow: {
    alignItems: 'center',
    marginTop: 6,
  },
  otpSuccessText: {
    color: '#2d6a4f',
    fontWeight: '600',
    fontSize: 13,
  },
  otpErrorText: {
    color: '#c62828',
    fontWeight: '500',
    fontSize: 13,
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
  submitBtnDisabled: {
    opacity: 0.5,
  },
});
