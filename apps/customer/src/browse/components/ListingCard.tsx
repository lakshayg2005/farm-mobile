import React from 'react';
import { View, StyleSheet, Image, TouchableOpacity, Text as RNText } from 'react-native';
import { useRouter } from 'expo-router';
import { Card, Text } from '@farm/ui';
import { formatDistance, formatGradeBadge, formatHarvestDate } from '../format.ts';
import { formatRupees } from '../../checkout/format.ts';
import { addToCart } from '../../checkout/cartStore.ts';
import { VerifiedBadge } from './VerifiedBadge.tsx';
import type { ProduceListing } from '../types.ts';

interface ListingCardProps {
  listing: ProduceListing;
  onAddedToCart?: (listing: ProduceListing) => void;
}

export function ListingCard({ listing, onAddedToCart }: ListingCardProps) {
  const router = useRouter();
  const photo = listing.photos[0];
  const gradeMeta = formatGradeBadge(listing.grade);

  const handleAddToCart = () => {
    const added = addToCart({
      listingId: listing.id,
      farmId: listing.farmId,
      farmName: listing.farmName,
      name: `${listing.cropName}${listing.variety ? ` (${listing.variety})` : ''}`,
      unit: listing.unit,
      unitPrice: listing.pricePerUnit,
      qty: 1,
      maxQty: listing.qtyAvailable,
    });
    if (added && onAddedToCart) onAddedToCart(listing);
  };

  const handleOpenDetail = () => {
    router.push({
      pathname: '/listing-detail' as never,
      params: { id: listing.id },
    });
  };

  return (
    <TouchableOpacity activeOpacity={0.9} onPress={handleOpenDetail}>
      <Card style={styles.card}>
        <View style={styles.imageContainer}>
          {photo?.url ? (
            <Image source={{ uri: photo.thumbUrl || photo.url }} style={styles.image} resizeMode="cover" />
          ) : (
            <View style={styles.imageFallback}>
              <RNText style={styles.fallbackEmoji}>🥕</RNText>
            </View>
          )}

          {/* Photo Authenticity Verified Badge */}
          <View style={styles.verifiedBadgeOverlay}>
            <VerifiedBadge photo={photo} compact />
          </View>

          {/* Organic Chip */}
          {listing.isOrganic && (
            <View style={styles.organicOverlay}>
              <RNText style={styles.organicText}>🌱 Organic</RNText>
            </View>
          )}
        </View>

        <View style={styles.body}>
          <View style={styles.topRow}>
            <View style={styles.titleArea}>
              <Text variant="h2" style={styles.cropTitle}>
                {listing.cropName}
              </Text>
              {listing.variety ? (
                <Text variant="caption" style={styles.varietyText}>
                  {listing.variety}
                </Text>
              ) : null}
            </View>

            {listing.grade && (
              <View style={[styles.gradeBadge, { backgroundColor: gradeMeta.badgeBg }]}>
                <RNText style={[styles.gradeText, { color: gradeMeta.badgeColor }]}>
                  {gradeMeta.label}
                </RNText>
              </View>
            )}
          </View>

          <View style={styles.farmRow}>
            <RNText style={styles.farmIcon}>🏡</RNText>
            <Text variant="label" style={styles.farmName}>
              {listing.farmName}
            </Text>
          </View>

          <View style={styles.metaRow}>
            <RNText style={styles.distanceText}>
              📍 {formatDistance(listing.distanceM)} away
            </RNText>
            <RNText style={styles.dot}>•</RNText>
            <Text variant="caption">{formatHarvestDate(listing.harvestDate)}</Text>
          </View>

          <View style={styles.footerRow}>
            <View>
              <Text variant="h2" style={styles.priceText}>
                {formatRupees(listing.pricePerUnit)}
                <RNText style={styles.unitText}> / {listing.unit}</RNText>
              </Text>
              <Text variant="caption" style={styles.stockText}>
                {listing.qtyAvailable} {listing.unit} in stock
              </Text>
            </View>

            <TouchableOpacity
              style={styles.addButton}
              activeOpacity={0.8}
              onPress={handleAddToCart}
              accessibilityLabel={`Add ${listing.cropName} to cart`}
            >
              <RNText style={styles.addButtonText}>+ Add</RNText>
            </TouchableOpacity>
          </View>
        </View>
      </Card>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: 0,
    overflow: 'hidden',
    borderRadius: 14,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e8e8e3',
    marginBottom: 14,
  },
  imageContainer: {
    height: 160,
    width: '100%',
    backgroundColor: '#e6ede8',
    position: 'relative',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  imageFallback: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  fallbackEmoji: {
    fontSize: 48,
  },
  verifiedBadgeOverlay: {
    position: 'absolute',
    top: 10,
    left: 10,
  },
  organicOverlay: {
    position: 'absolute',
    top: 10,
    right: 10,
    backgroundColor: 'rgba(27, 67, 50, 0.9)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  organicText: {
    color: '#fff',
    fontSize: 11,
    fontWeight: '700',
  },
  body: {
    padding: 14,
    gap: 6,
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  titleArea: {
    flex: 1,
  },
  cropTitle: {
    fontSize: 19,
    fontWeight: '700',
    color: '#1b1b1b',
  },
  varietyText: {
    color: '#555',
    fontSize: 12,
    marginTop: 1,
  },
  gradeBadge: {
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 5,
    marginLeft: 8,
  },
  gradeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  farmRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: 2,
  },
  farmIcon: {
    fontSize: 13,
  },
  farmName: {
    color: '#2d6a4f',
    fontWeight: '600',
    fontSize: 13,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  distanceText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#40916c',
  },
  dot: {
    fontSize: 12,
    color: '#999',
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#f0f0eb',
  },
  priceText: {
    fontSize: 20,
    fontWeight: '800',
    color: '#1b1b1b',
  },
  unitText: {
    fontSize: 13,
    fontWeight: '500',
    color: '#666',
  },
  stockText: {
    fontSize: 11,
    color: '#777',
    marginTop: 2,
  },
  addButton: {
    backgroundColor: '#2d6a4f',
    paddingHorizontal: 18,
    paddingVertical: 8,
    borderRadius: 8,
  },
  addButtonText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 14,
  },
});
