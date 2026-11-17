import React, { useState } from 'react';
import { View, StyleSheet, Image, TouchableOpacity, ScrollView, Text as RNText } from 'react-native';
import { Card, Text, Button } from '@farm/ui';
import { formatRupees } from '../../checkout/format.ts';
import { VerifiedBadge } from '../../browse/components/VerifiedBadge.tsx';
import { getStageMetadata } from '../filterLogic.ts';
import type { ReservationItem, CropStage } from '../types.ts';

interface CropTimelineViewProps {
  reservation: ReservationItem;
  onClose?: () => void;
}

const ALL_STAGES: CropStage[] = ['SOWN', 'GROWING', 'FLOWERING', 'HARVEST_READY'];

export function CropTimelineView({ reservation, onClose }: CropTimelineViewProps) {
  const [harvestChoice, setHarvestChoice] = useState<'DELIVERY' | 'BUYBACK' | null>(null);

  const updatesByStage = new Map(reservation.updates.map((u) => [u.stage, u]));

  // Find active stage index
  const activeMeta = getStageMetadata(
    reservation.status === 'HARVEST_READY'
      ? 'HARVEST_READY'
      : (reservation.updates[reservation.updates.length - 1]?.stage || 'SOWN')
  );

  return (
    <ScrollView contentContainerStyle={styles.container}>
      {/* Top Header Card */}
      <Card style={styles.headerCard}>
        <View style={styles.topRow}>
          <View style={{ flex: 1 }}>
            <Text variant="caption" style={styles.resId}>
              Reservation ID: {reservation.id}
            </Text>
            <Text variant="h1" style={styles.cropTitle}>
              {reservation.cropName}
            </Text>
            <Text variant="caption" style={styles.plotSubtitle}>
              🏡 {reservation.plotTitle} ({reservation.seasonName})
            </Text>
          </View>

          <View style={[styles.statusBadge, { backgroundColor: activeMeta.bgColor }]}>
            <RNText style={[styles.statusBadgeText, { color: activeMeta.color }]}>
              {reservation.status.replace(/_/g, ' ')}
            </RNText>
          </View>
        </View>

        <View style={styles.detailsRow}>
          <View>
            <Text variant="caption">Target Harvest</Text>
            <Text variant="label">{reservation.expectedQtyKg} kg</Text>
          </View>
          <View>
            <Text variant="caption">Deposit Paid</Text>
            <Text variant="label">{formatRupees(reservation.deposit)}</Text>
          </View>
          <View>
            <Text variant="caption">Farmer Contact</Text>
            <Text variant="label">{reservation.farmerName}</Text>
          </View>
        </View>
      </Card>

      {/* Stepper Progress Bar */}
      <View style={styles.stepperContainer}>
        {ALL_STAGES.map((stg, i) => {
          const meta = getStageMetadata(stg);
          const isPassedOrCurrent = meta.stepIndex <= activeMeta.stepIndex;
          const isCurrent = meta.stepIndex === activeMeta.stepIndex;

          return (
            <React.Fragment key={stg}>
              <View style={styles.stepNode}>
                <View
                  style={[
                    styles.stepCircle,
                    isPassedOrCurrent && { backgroundColor: meta.color, borderColor: meta.color },
                    isCurrent && styles.stepCircleActive,
                  ]}
                >
                  <RNText style={styles.stepIcon}>{meta.icon}</RNText>
                </View>
                <RNText style={[styles.stepLabel, isPassedOrCurrent && styles.stepLabelActive]}>
                  {meta.label.split(' ')[0]}
                </RNText>
              </View>
              {i < ALL_STAGES.length - 1 && (
                <View
                  style={[
                    styles.stepConnector,
                    meta.stepIndex < activeMeta.stepIndex && { backgroundColor: '#2d6a4f' },
                  ]}
                />
              )}
            </React.Fragment>
          );
        })}
      </View>

      {/* Harvest Resolution Section (When HARVEST_READY) */}
      {reservation.status === 'HARVEST_READY' && (
        <Card style={styles.harvestCard}>
          <View style={styles.harvestTitleRow}>
            <RNText style={styles.harvestTrophy}>🎉</RNText>
            <View style={{ flex: 1 }}>
              <Text variant="h2" style={{ color: '#6b21a8' }}>
                Your Crop is Ready for Harvest!
              </Text>
              <Text variant="caption">
                Quality inspection passed with Grade A. Select your fulfillment preference below:
              </Text>
            </View>
          </View>

          <View style={styles.choiceRow}>
            <TouchableOpacity
              style={[
                styles.choiceBtn,
                harvestChoice === 'DELIVERY' && styles.choiceBtnSelected,
              ]}
              onPress={() => setHarvestChoice('DELIVERY')}
            >
              <RNText style={styles.choiceEmoji}>🚚</RNText>
              <RNText style={styles.choiceTitle}>Doorstep Delivery</RNText>
              <RNText style={styles.choiceDesc}>
                Deliver all {reservation.expectedQtyKg} kg of farm fresh harvest to your home address via pooled delivery.
              </RNText>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.choiceBtn,
                harvestChoice === 'BUYBACK' && styles.choiceBtnSelected,
              ]}
              onPress={() => setHarvestChoice('BUYBACK')}
            >
              <RNText style={styles.choiceEmoji}>💰</RNText>
              <RNText style={styles.choiceTitle}>Guaranteed Buy-Back</RNText>
              <RNText style={styles.choiceDesc}>
                Farmer sells the crop to local agri-markets and deposits the harvest earnings directly into your bank account.
              </RNText>
            </TouchableOpacity>
          </View>

          {harvestChoice && (
            <View style={styles.choiceConfirmBox}>
              <RNText style={styles.choiceConfirmText}>
                ✓ Preference saved: {harvestChoice === 'DELIVERY' ? 'Doorstep Delivery' : 'Guaranteed Buy-Back payout'}. Logistics coordinator notified.
              </RNText>
            </View>
          )}
        </Card>
      )}

      {/* Growth Stage Timeline Cards */}
      <View style={styles.timelineSection}>
        <Text variant="h2" style={styles.timelineHeading}>
          Growth Timeline & Field Updates
        </Text>

        {reservation.updates.map((update, idx) => {
          const meta = getStageMetadata(update.stage);
          return (
            <Card key={update.id} style={styles.updateCard}>
              <View style={styles.updateHeader}>
                <View style={[styles.stagePill, { backgroundColor: meta.bgColor }]}>
                  <RNText style={styles.stageEmoji}>{meta.icon}</RNText>
                  <RNText style={[styles.stageName, { color: meta.color }]}>
                    {update.stageTitle || meta.label}
                  </RNText>
                </View>
                <Text variant="caption">{update.createdAt}</Text>
              </View>

              {update.note ? (
                <Text variant="body" style={styles.updateNote}>
                  "{update.note}"
                </Text>
              ) : null}

              {/* Photos & Geolocation Verification Badges */}
              {update.photos && update.photos.length > 0 && (
                <View style={styles.photosWrapper}>
                  {update.photos.map((photo) => (
                    <View key={photo.id} style={styles.photoContainer}>
                      <Image source={{ uri: photo.url }} style={styles.photoImage} resizeMode="cover" />
                      <View style={styles.photoBadgeOverlay}>
                        <VerifiedBadge
                          photo={{
                            id: photo.id,
                            url: photo.url,
                            thumbUrl: photo.thumbUrl,
                            isGeoVerified: photo.isGeoVerified,
                            geoStatus: photo.isGeoVerified ? 'VERIFIED' : 'NO_GPS',
                            distanceM: photo.distanceM,
                          }}
                          compact
                        />
                      </View>
                    </View>
                  ))}
                </View>
              )}
            </Card>
          );
        })}
      </View>

      {onClose && (
        <Button
          label="← Back to Land Plots"
          variant="secondary"
          onPress={onClose}
          style={{ marginTop: 12, marginBottom: 20 }}
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    gap: 14,
    backgroundColor: '#f8fafc',
  },
  headerCard: {
    padding: 16,
    gap: 12,
    borderRadius: 14,
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  resId: {
    color: '#64748b',
    fontSize: 11,
  },
  cropTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: '#1e293b',
  },
  plotSubtitle: {
    color: '#475569',
    marginTop: 2,
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  statusBadgeText: {
    fontSize: 11,
    fontWeight: '800',
  },
  detailsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
  },
  stepperContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 10,
    paddingHorizontal: 8,
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  stepNode: {
    alignItems: 'center',
    gap: 4,
  },
  stepCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#f1f5f9',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#cbd5e1',
  },
  stepCircleActive: {
    transform: [{ scale: 1.15 }],
  },
  stepIcon: {
    fontSize: 16,
  },
  stepLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: '#64748b',
  },
  stepLabelActive: {
    color: '#0f172a',
    fontWeight: '700',
  },
  stepConnector: {
    flex: 1,
    height: 3,
    backgroundColor: '#e2e8f0',
    marginHorizontal: 4,
    marginTop: -14,
  },
  harvestCard: {
    backgroundColor: '#faf5ff',
    borderWidth: 1.5,
    borderColor: '#d8b4fe',
    padding: 16,
    borderRadius: 14,
    gap: 12,
  },
  harvestTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  harvestTrophy: {
    fontSize: 32,
  },
  choiceRow: {
    flexDirection: 'row',
    gap: 10,
  },
  choiceBtn: {
    flex: 1,
    backgroundColor: '#fff',
    borderWidth: 1.5,
    borderColor: '#e9d5ff',
    borderRadius: 10,
    padding: 12,
    alignItems: 'center',
    gap: 4,
  },
  choiceBtnSelected: {
    borderColor: '#7e22ce',
    backgroundColor: '#f3e8ff',
  },
  choiceEmoji: {
    fontSize: 24,
  },
  choiceTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#581c87',
    textAlign: 'center',
  },
  choiceDesc: {
    fontSize: 10,
    color: '#6b21a8',
    textAlign: 'center',
    lineHeight: 14,
  },
  choiceConfirmBox: {
    backgroundColor: '#f3e8ff',
    padding: 10,
    borderRadius: 8,
  },
  choiceConfirmText: {
    fontSize: 12,
    color: '#581c87',
    fontWeight: '700',
  },
  timelineSection: {
    gap: 10,
  },
  timelineHeading: {
    fontSize: 16,
    fontWeight: '700',
    color: '#334155',
  },
  updateCard: {
    padding: 14,
    borderRadius: 12,
    gap: 10,
  },
  updateHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  stagePill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  stageEmoji: {
    fontSize: 13,
  },
  stageName: {
    fontSize: 12,
    fontWeight: '700',
  },
  updateNote: {
    color: '#334155',
    fontStyle: 'italic',
    lineHeight: 19,
  },
  photosWrapper: {
    gap: 8,
  },
  photoContainer: {
    height: 180,
    borderRadius: 10,
    overflow: 'hidden',
    backgroundColor: '#e2e8f0',
    position: 'relative',
  },
  photoImage: {
    width: '100%',
    height: '100%',
  },
  photoBadgeOverlay: {
    position: 'absolute',
    top: 8,
    left: 8,
  },
});
