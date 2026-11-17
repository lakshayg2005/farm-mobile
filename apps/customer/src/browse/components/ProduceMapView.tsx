import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity, ScrollView, Text as RNText } from 'react-native';
import { useRouter } from 'expo-router';
import { Button, Card, Text } from '@farm/ui';
import { formatDistance, formatRupees } from '../../checkout/format.ts';
import { addToCart } from '../../checkout/cartStore.ts';
import { VerifiedBadge } from './VerifiedBadge.tsx';
import type { ProduceListing, LocationCoords } from '../types.ts';

interface ProduceMapViewProps {
  listings: ProduceListing[];
  userLocation: LocationCoords;
  onAddedToCart?: (listing: ProduceListing) => void;
}

export function ProduceMapView({ listings, userLocation, onAddedToCart }: ProduceMapViewProps) {
  const router = useRouter();
  const [selectedListingId, setSelectedListingId] = useState<string | null>(
    listings[0]?.id ?? null,
  );

  const selectedListing = listings.find((l) => l.id === selectedListingId) || listings[0];

  const handleAddToCart = (listing: ProduceListing) => {
    addToCart({
      listingId: listing.id,
      farmId: listing.farmId,
      farmName: listing.farmName,
      name: `${listing.cropName}${listing.variety ? ` (${listing.variety})` : ''}`,
      unit: listing.unit,
      unitPrice: listing.pricePerUnit,
      qty: 1,
      maxQty: listing.qtyAvailable,
    });
    if (onAddedToCart) onAddedToCart(listing);
  };

  const handleOpenDetail = (listing: ProduceListing) => {
    router.push({
      pathname: '/listing-detail' as any,
      params: { id: listing.id },
    });
  };

  return (
    <View style={styles.container}>
      {/* Map Canvas Visualizer */}
      <View style={styles.mapCanvas}>
        <View style={styles.mapGridPattern}>
          {/* Central Customer Pin */}
          <View style={styles.centerMarker}>
            <View style={styles.pulseRing} />
            <View style={styles.userPin}>
              <RNText style={styles.userPinIcon}>📍</RNText>
            </View>
            <View style={styles.userLabelCard}>
              <RNText style={styles.userLabelText}>You ({userLocation.name})</RNText>
            </View>
          </View>

          {/* Listing Pins scattered around */}
          {listings.map((item, idx) => {
            const isSelected = item.id === (selectedListing?.id);
            // Derive relative display position based on index or coordinates
            const angles = [35, 120, 210, 290, 80, 160, 240, 320];
            const distRadius = [70, 110, 95, 130, 85, 120, 105, 135];
            const angle = angles[idx % angles.length];
            const r = distRadius[idx % distRadius.length];
            const rad = (angle * Math.PI) / 180;
            const offsetX = Math.cos(rad) * r;
            const offsetY = Math.sin(rad) * r;

            return (
              <TouchableOpacity
                key={item.id}
                activeOpacity={0.8}
                onPress={() => setSelectedListingId(item.id)}
                style={[
                  styles.listingPin,
                  {
                    transform: [{ translateX: offsetX }, { translateY: offsetY }],
                  },
                ]}
              >
                <View
                  style={[
                    styles.pinBubble,
                    isSelected && styles.pinBubbleSelected,
                    item.isOrganic && styles.pinBubbleOrganic,
                  ]}
                >
                  <RNText style={styles.pinCropEmoji}>
                    {item.cropId === 'tomato'
                      ? '🍅'
                      : item.cropId === 'spinach'
                      ? '🥬'
                      : item.cropId === 'onion'
                      ? '🧅'
                      : item.cropId === 'potato'
                      ? '🥔'
                      : item.cropId === 'mango'
                      ? '🥭'
                      : '🥕'}
                  </RNText>
                  <RNText
                    style={[styles.pinText, isSelected && styles.pinTextSelected]}
                  >
                    {formatRupees(item.pricePerUnit)}
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
            🗺 Showing {listings.length} farms within radius of {userLocation.name}
          </RNText>
        </View>
      </View>

      {/* Selected Listing Bottom Carousel / Preview Card */}
      {selectedListing && (
        <View style={styles.previewContainer}>
          <Card style={styles.previewCard}>
            <View style={styles.previewRow}>
              <View style={{ flex: 1 }}>
                <View style={styles.previewTitleRow}>
                  <Text variant="h2" style={styles.previewTitle}>
                    {selectedListing.cropName}
                  </Text>
                  {selectedListing.isOrganic && (
                    <View style={styles.miniOrganic}>
                      <RNText style={styles.miniOrganicText}>🌱 Organic</RNText>
                    </View>
                  )}
                </View>

                <Text variant="label" style={styles.previewFarm}>
                  🏡 {selectedListing.farmName} · {selectedListing.village || selectedListing.district}
                </Text>

                <View style={styles.previewMeta}>
                  <RNText style={styles.previewDist}>
                    📍 {formatDistance(selectedListing.distanceM)} away
                  </RNText>
                  <RNText style={styles.dot}>•</RNText>
                  <Text variant="caption">
                    {selectedListing.qtyAvailable} {selectedListing.unit} left
                  </Text>
                </View>

                {/* Photo Verified Badge */}
                <View style={{ marginTop: 6 }}>
                  <VerifiedBadge photo={selectedListing.photos[0]} compact />
                </View>
              </View>

              <View style={styles.priceColumn}>
                <Text variant="h2" style={styles.previewPrice}>
                  {formatRupees(selectedListing.pricePerUnit)}
                </Text>
                <Text variant="caption">per {selectedListing.unit}</Text>
              </View>
            </View>

            <View style={styles.previewActions}>
              <Button
                label="View Details"
                variant="secondary"
                style={{ flex: 1 }}
                onPress={() => handleOpenDetail(selectedListing)}
              />
              <Button
                label="+ Add to Cart"
                style={{ flex: 1 }}
                onPress={() => handleAddToCart(selectedListing)}
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
    backgroundColor: '#e6ede8',
    position: 'relative',
    marginVertical: 10,
    borderWidth: 1,
    borderColor: '#d0ddd4',
  },
  mapCanvas: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#d8e7dc',
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
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#cbd5e1',
  },
  mapStatusText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#2d6a4f',
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
    backgroundColor: 'rgba(45, 106, 79, 0.2)',
  },
  userPin: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#2d6a4f',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#fff',
  },
  userPinIcon: {
    fontSize: 14,
  },
  userLabelCard: {
    backgroundColor: '#1b4332',
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
  listingPin: {
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
    borderRadius: 16,
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
  pinBubbleOrganic: {
    borderColor: '#15803d',
  },
  pinCropEmoji: {
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
    gap: 10,
  },
  previewRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  previewTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  previewTitle: {
    fontSize: 17,
    fontWeight: '700',
  },
  miniOrganic: {
    backgroundColor: '#dcfce7',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  miniOrganicText: {
    color: '#166534',
    fontSize: 10,
    fontWeight: '700',
  },
  previewFarm: {
    color: '#40916c',
    fontSize: 12,
    marginTop: 2,
  },
  previewMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 4,
  },
  previewDist: {
    fontSize: 12,
    fontWeight: '600',
    color: '#2d6a4f',
  },
  dot: {
    color: '#999',
    fontSize: 10,
  },
  priceColumn: {
    alignItems: 'flex-end',
  },
  previewPrice: {
    fontSize: 18,
    fontWeight: '800',
    color: '#1b1b1b',
  },
  previewActions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
});
