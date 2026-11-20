import React, { useEffect, useState } from 'react';
import {
  ScrollView,
  StyleSheet,
  View,
  Image,
  TouchableOpacity,
  ActivityIndicator,
  Text as RNText,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Button, Card, Text } from '@farm/ui';
import { formatRupees, formatQty } from '../src/checkout/format.ts';
import { addToCart, useCart } from '../src/checkout/cartStore.ts';
import {
  fetchListingDetail,
  formatDistance,
  formatGradeBadge,
  formatHarvestDate,
  VerifiedBadge,
  type ProduceListing,
} from '../src/browse/index.ts';

export default function ListingDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const cart = useCart();

  const [listing, setListing] = useState<ProduceListing | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [qty, setQty] = useState<number>(1);
  const [addedMessage, setAddedMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    fetchListingDetail(id)
      .then((data) => {
        setListing(data);
        setIsLoading(false);
      })
      .catch((err) => {
        setError(err.message || 'Failed to load produce listing');
        setIsLoading(false);
      });
  }, [id]);

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#2d6a4f" />
        <Text variant="caption" style={{ marginTop: 12 }}>
          Loading produce details...
        </Text>
      </View>
    );
  }

  if (error || !listing) {
    return (
      <View style={styles.errorContainer}>
        <RNText style={styles.errorIcon}>⚠️</RNText>
        <Text variant="h2">Listing not found</Text>
        <Text variant="caption" style={styles.errorText}>
          {error || 'This listing may have been removed or sold out.'}
        </Text>
        <Button label="Back to Produce" onPress={() => router.back()} style={{ marginTop: 16 }} />
      </View>
    );
  }

  const photo = listing.photos[0];
  const gradeMeta = formatGradeBadge(listing.grade);
  const cartItem = cart.items.find((i) => i.listingId === listing.id);
  const subtotal = Math.round(listing.pricePerUnit * qty * 100) / 100;

  const handleAddToCart = () => {
    const added = addToCart({
      listingId: listing.id,
      farmId: listing.farmId,
      farmName: listing.farmName,
      name: `${listing.cropName}${listing.variety ? ` (${listing.variety})` : ''}`,
      unit: listing.unit,
      unitPrice: listing.pricePerUnit,
      qty,
      maxQty: listing.qtyAvailable,
    });
    if (!added) {
      setAddedMessage('This is an offline demo listing and cannot be ordered. Connect to the server and refresh.');
      setTimeout(() => setAddedMessage(null), 4000);
      return;
    }
    setAddedMessage(`✓ Added ${qty} ${listing.unit} to cart!`);
    setTimeout(() => setAddedMessage(null), 4000);
  };

  return (
    <View style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content}>
        {/* Top Navigation Bar */}
        <View style={styles.navBar}>
          <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
            <RNText style={styles.backButtonText}>← Back</RNText>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => router.push('/cart')} style={styles.cartPill}>
            <RNText style={styles.cartPillText}>
              🛒 Cart ({cart.items.length})
            </RNText>
          </TouchableOpacity>
        </View>

        {/* Hero Photo Section */}
        <View style={styles.heroImageContainer}>
          {photo?.url ? (
            <Image source={{ uri: photo.url }} style={styles.heroImage} resizeMode="cover" />
          ) : (
            <View style={styles.imageFallback}>
              <RNText style={styles.fallbackEmoji}>🥕</RNText>
            </View>
          )}

          {listing.isOrganic && (
            <View style={styles.organicBadge}>
              <RNText style={styles.organicBadgeText}>🌱 100% Organic</RNText>
            </View>
          )}
        </View>

        {/* Photo Authenticity & Geolocation Verification Card */}
        <Card style={styles.verificationCard}>
          <Text variant="label" style={styles.verificationHeading}>
            Produce Photo Verification
          </Text>
          <VerifiedBadge photo={photo} />
        </Card>

        {/* Produce Header Card */}
        <Card style={styles.card}>
          <View style={styles.headerRow}>
            <View style={{ flex: 1 }}>
              <Text variant="h1" style={styles.cropTitle}>
                {listing.cropName}
              </Text>
              {listing.variety ? (
                <Text variant="body" style={styles.varietyText}>
                  Variety: {listing.variety}
                </Text>
              ) : null}
            </View>

            {listing.grade && (
              <View style={[styles.gradeBadge, { backgroundColor: gradeMeta.badgeBg }]}>
                <RNText style={[styles.gradeBadgeText, { color: gradeMeta.badgeColor }]}>
                  {gradeMeta.label}
                </RNText>
              </View>
            )}
          </View>

          <View style={styles.priceRow}>
            <Text variant="h1" style={styles.price}>
              {formatRupees(listing.pricePerUnit)}
            </Text>
            <Text variant="body" style={styles.priceUnit}>
              / {listing.unit}
            </Text>
            <View style={styles.stockPill}>
              <RNText style={styles.stockPillText}>
                {listing.qtyAvailable} {listing.unit} in stock
              </RNText>
            </View>
          </View>

          {listing.description ? (
            <Text variant="body" style={styles.description}>
              {listing.description}
            </Text>
          ) : null}

          {/* Quality Grade Note */}
          <View style={[styles.qualityNote, { backgroundColor: gradeMeta.badgeBg }]}>
            <RNText style={[styles.qualityNoteText, { color: gradeMeta.badgeColor }]}>
              🔍 {gradeMeta.description}
            </RNText>
          </View>
        </Card>

        {/* Farm & Origin Info */}
        <Card style={styles.card}>
          <Text variant="h2" style={styles.sectionTitle}>
            Farm & Origin
          </Text>

          <View style={styles.infoRow}>
            <RNText style={styles.infoIcon}>🏡</RNText>
            <View style={{ flex: 1 }}>
              <Text variant="label">Farm Name</Text>
              <Text variant="body">{listing.farmName}</Text>
            </View>
          </View>

          {listing.farmerName && (
            <View style={styles.infoRow}>
              <RNText style={styles.infoIcon}>👨‍🌾</RNText>
              <View style={{ flex: 1 }}>
                <Text variant="label">Farmer</Text>
                <Text variant="body">{listing.farmerName}</Text>
              </View>
            </View>
          )}

          <View style={styles.infoRow}>
            <RNText style={styles.infoIcon}>📍</RNText>
            <View style={{ flex: 1 }}>
              <Text variant="label">Location & Distance</Text>
              <Text variant="body">
                {listing.village ? `${listing.village}, ` : ''}{listing.district} (
                {formatDistance(listing.distanceM)} away)
              </Text>
            </View>
          </View>

          <View style={styles.infoRow}>
            <RNText style={styles.infoIcon}>📅</RNText>
            <View style={{ flex: 1 }}>
              <Text variant="label">Harvest Date</Text>
              <Text variant="body">{formatHarvestDate(listing.harvestDate)}</Text>
            </View>
          </View>
        </Card>

        {/* Feedback Banner if added */}
        {addedMessage && (
          <View style={styles.successBanner}>
            <RNText style={styles.successText}>{addedMessage}</RNText>
            <TouchableOpacity onPress={() => router.push('/cart')}>
              <RNText style={styles.viewCartLink}>View Cart →</RNText>
            </TouchableOpacity>
          </View>
        )}
      </ScrollView>

      {/* Sticky Bottom Purchase Action Bar */}
      <View style={styles.footer}>
        <View style={styles.stepperSection}>
          <Text variant="caption">Quantity ({listing.unit})</Text>
          <View style={styles.stepper}>
            <TouchableOpacity
              style={styles.stepBtn}
              onPress={() => setQty((prev) => Math.max(1, prev - 1))}
              disabled={qty <= 1}
            >
              <RNText style={styles.stepBtnText}>−</RNText>
            </TouchableOpacity>
            <RNText style={styles.qtyDisplay}>{formatQty(qty)}</RNText>
            <TouchableOpacity
              style={styles.stepBtn}
              onPress={() => setQty((prev) => Math.min(listing.qtyAvailable, prev + 1))}
              disabled={qty >= listing.qtyAvailable}
            >
              <RNText style={styles.stepBtnText}>+</RNText>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.actionColumn}>
          <Text variant="caption">Subtotal</Text>
          <Text variant="h2" style={styles.subtotalText}>
            {formatRupees(subtotal)}
          </Text>
        </View>

        <Button
          label={cartItem ? 'Add More' : 'Add to Cart'}
          onPress={handleAddToCart}
          style={styles.addToCartBtn}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#f5f5f0',
  },
  content: {
    padding: 16,
    paddingBottom: 110,
    gap: 14,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f5f5f0',
  },
  errorContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: '#f5f5f0',
  },
  errorIcon: {
    fontSize: 48,
    marginBottom: 12,
  },
  errorText: {
    textAlign: 'center',
    marginTop: 6,
    color: '#666',
  },
  navBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  backButton: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    backgroundColor: '#fff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e2e2dc',
  },
  backButtonText: {
    color: '#2d6a4f',
    fontWeight: '700',
    fontSize: 14,
  },
  cartPill: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    backgroundColor: '#2d6a4f',
    borderRadius: 20,
  },
  cartPillText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 13,
  },
  heroImageContainer: {
    height: 240,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: '#e6ede8',
    position: 'relative',
    borderWidth: 1,
    borderColor: '#d0ddd4',
  },
  heroImage: {
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
    fontSize: 64,
  },
  organicBadge: {
    position: 'absolute',
    top: 14,
    right: 14,
    backgroundColor: '#1b4332',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  organicBadgeText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 12,
  },
  verificationCard: {
    padding: 12,
    gap: 8,
    borderRadius: 12,
  },
  verificationHeading: {
    color: '#444',
    fontWeight: '600',
  },
  card: {
    padding: 16,
    gap: 12,
    borderRadius: 14,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  cropTitle: {
    fontSize: 24,
    fontWeight: '800',
  },
  varietyText: {
    color: '#666',
    marginTop: 2,
  },
  gradeBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
  },
  gradeBadgeText: {
    fontWeight: '800',
    fontSize: 13,
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 4,
  },
  price: {
    fontSize: 28,
    fontWeight: '900',
    color: '#1b1b1b',
  },
  priceUnit: {
    fontSize: 16,
    color: '#666',
  },
  stockPill: {
    marginLeft: 'auto',
    backgroundColor: '#f0fdf4',
    borderWidth: 1,
    borderColor: '#bbf7d0',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  stockPillText: {
    fontSize: 12,
    color: '#166534',
    fontWeight: '600',
  },
  description: {
    color: '#444',
    lineHeight: 21,
  },
  qualityNote: {
    padding: 10,
    borderRadius: 8,
  },
  qualityNoteText: {
    fontSize: 12,
    fontWeight: '600',
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 4,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  infoIcon: {
    fontSize: 20,
  },
  successBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#dcfce7',
    borderWidth: 1,
    borderColor: '#86efac',
    padding: 14,
    borderRadius: 10,
  },
  successText: {
    color: '#166534',
    fontWeight: '700',
    fontSize: 14,
  },
  viewCartLink: {
    color: '#1b4332',
    fontWeight: '800',
    textDecorationLine: 'underline',
  },
  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderTopColor: '#e5e7eb',
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  stepperSection: {
    gap: 2,
  },
  stepper: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    backgroundColor: '#f9fafb',
  },
  stepBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  stepBtnText: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1f2937',
  },
  qtyDisplay: {
    fontSize: 15,
    fontWeight: '700',
    paddingHorizontal: 8,
    minWidth: 28,
    textAlign: 'center',
  },
  actionColumn: {
    marginLeft: 4,
  },
  subtotalText: {
    fontSize: 18,
    fontWeight: '800',
    color: '#1b1b1b',
  },
  addToCartBtn: {
    flex: 1,
    marginLeft: 'auto',
  },
});
