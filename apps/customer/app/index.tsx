import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
  Modal,
  Text as RNText,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Text, Card, Button } from '@farm/ui';
import { useAuth } from '@farm/api';
import { useCart } from '../src/checkout/cartStore.ts';
import { usePushTokenRegistration } from './usePushToken';
import {
  useProduceBrowse,
  ListingCard,
  ProduceMapView,
  FilterSheet,
  POPULAR_DISTRICTS,
} from '../src/browse/index.ts';

const CROPS_LIST = [
  { id: 'ALL', label: 'All', emoji: '🌱' },
  { id: 'tomato', label: 'Tomato', emoji: '🍅' },
  { id: 'spinach', label: 'Spinach', emoji: '🥬' },
  { id: 'onion', label: 'Onion', emoji: '🧅' },
  { id: 'potato', label: 'Potato', emoji: '🥔' },
  { id: 'okra', label: 'Okra', emoji: '🌿' },
  { id: 'carrot', label: 'Carrot', emoji: '🥕' },
  { id: 'mango', label: 'Mango', emoji: '🥭' },
];

export default function CustomerHomeScreen() {
  const router = useRouter();
  const { isAuthenticated, accessToken } = useAuth();
  usePushTokenRegistration(isAuthenticated ? accessToken : null);
  const cart = useCart();

  const [activeTab, setActiveTab] = useState<'produce' | 'land'>('produce');
  const [filterSheetVisible, setFilterSheetVisible] = useState(false);
  const [locationPickerVisible, setLocationPickerVisible] = useState(false);

  const {
    userLocation,
    setUserLocation,
    filters,
    setFilters,
    setCropFilter,
    resetFilters,
    viewMode,
    setViewMode,
    listings,
    totalCount,
    isLoading,
    error,
    refresh,
    toastMessage,
    handleAddedToCart,
  } = useProduceBrowse();

  const activeFilterCount =
    (filters.organicOnly ? 1 : 0) +
    (filters.minGrade !== 'ALL' ? 1 : 0) +
    (filters.minPrice !== null ? 1 : 0) +
    (filters.maxPrice !== null ? 1 : 0) +
    (filters.radiusKm !== 15 ? 1 : 0);

  const cartItemCount = cart.items.reduce((sum, item) => sum + item.qty, 0);

  return (
    <View style={styles.container}>
      {/* Top Header Bar */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text variant="h1" style={styles.appTitle}>
            🥕 Farm Fresh
          </Text>
          <TouchableOpacity
            style={styles.locationPill}
            onPress={() => setLocationPickerVisible(true)}
            activeOpacity={0.8}
          >
            <RNText style={styles.locationPinIcon}>📍</RNText>
            <RNText style={styles.locationText}>{userLocation.name}</RNText>
            <RNText style={styles.locationArrow}>▼</RNText>
          </TouchableOpacity>
        </View>

        <View style={styles.headerRight}>
          {/* Cart Icon with Live Count Badge */}
          <TouchableOpacity
            style={styles.cartButton}
            onPress={() => router.push('/cart')}
            accessibilityLabel="Shopping Cart"
          >
            <RNText style={styles.cartIcon}>🛒</RNText>
            {cartItemCount > 0 && (
              <View style={styles.cartBadge}>
                <RNText style={styles.cartBadgeText}>
                  {cartItemCount > 99 ? '99+' : cartItemCount}
                </RNText>
              </View>
            )}
          </TouchableOpacity>

          {isAuthenticated && (
            <TouchableOpacity
              style={styles.profileButton}
              accessibilityLabel="Notifications"
              onPress={() => router.push('/notifications' as never)}
            >
              <RNText style={styles.profileIcon}>🔔</RNText>
            </TouchableOpacity>
          )}

          {/* User profile / login shortcut */}
          <TouchableOpacity
            style={styles.profileButton}
            onPress={() => {
              if (isAuthenticated) {
                router.push('/orders' as never);
              } else {
                router.push('/login');
              }
            }}
          >
            <RNText style={styles.profileIcon}>{isAuthenticated ? '👤' : '🔑'}</RNText>
          </TouchableOpacity>
        </View>
      </View>

      {/* Main Tab Navigation: Produce (Sprint 2) vs Land (Sprint 4) */}
      <View style={styles.tabBar}>
        <TouchableOpacity
          style={[styles.tabItem, activeTab === 'produce' && styles.tabItemActive]}
          onPress={() => setActiveTab('produce')}
        >
          <RNText
            style={[styles.tabItemText, activeTab === 'produce' && styles.tabItemTextActive]}
          >
            🥕 Produce (Browse & Buy)
          </RNText>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.tabItem, activeTab === 'land' && styles.tabItemActive]}
          onPress={() => setActiveTab('land')}
        >
          <RNText
            style={[styles.tabItemText, activeTab === 'land' && styles.tabItemTextActive]}
          >
            🌾 Land (Plots & Crops)
          </RNText>
        </TouchableOpacity>
      </View>

      {/* PRODUCE TAB CONTENT */}
      {activeTab === 'produce' && (
        <View style={{ flex: 1 }}>
          {/* Search Bar & View Mode Switcher */}
          <View style={styles.searchSection}>
            <View style={styles.searchBar}>
              <RNText style={styles.searchIcon}>🔍</RNText>
              <TextInput
                style={styles.searchInput}
                placeholder="Search produce, crop, or farm..."
                placeholderTextColor="#999"
                value={filters.query}
                onChangeText={(text) => setFilters((prev) => ({ ...prev, query: text }))}
              />
              {filters.query ? (
                <TouchableOpacity
                  onPress={() => setFilters((prev) => ({ ...prev, query: '' }))}
                  style={styles.clearBtn}
                >
                  <RNText style={styles.clearBtnText}>✕</RNText>
                </TouchableOpacity>
              ) : null}
            </View>

            {/* Filter Sheet Trigger */}
            <TouchableOpacity
              style={[styles.filterTrigger, activeFilterCount > 0 && styles.filterTriggerActive]}
              onPress={() => setFilterSheetVisible(true)}
            >
              <RNText style={styles.filterTriggerText}>
                ⚙ Filter{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
              </RNText>
            </TouchableOpacity>

            {/* View Mode Toggle Button (List vs Map) */}
            <TouchableOpacity
              style={styles.viewModeToggle}
              onPress={() => setViewMode((prev) => (prev === 'list' ? 'map' : 'list'))}
              accessibilityLabel={`Switch to ${viewMode === 'list' ? 'Map' : 'List'} view`}
            >
              <RNText style={styles.viewModeText}>
                {viewMode === 'list' ? '🗺 Map' : '📋 List'}
              </RNText>
            </TouchableOpacity>
          </View>

          {/* Crop Filter Horizontal Chips */}
          <View style={styles.cropChipsWrapper}>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.cropChipsScroll}
            >
              {CROPS_LIST.map((crop) => {
                const isSelected =
                  (crop.id === 'ALL' && !filters.crop) || filters.crop === crop.id;
                return (
                  <TouchableOpacity
                    key={crop.id}
                    style={[styles.cropChip, isSelected && styles.cropChipSelected]}
                    onPress={() => setCropFilter(crop.id === 'ALL' ? null : crop.id)}
                  >
                    <RNText style={styles.cropEmoji}>{crop.emoji}</RNText>
                    <RNText
                      style={[styles.cropChipLabel, isSelected && styles.cropChipLabelSelected]}
                    >
                      {crop.label}
                    </RNText>
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          </View>

          {/* Notification Toast Banner */}
          {toastMessage && (
            <View style={styles.toastBanner}>
              <RNText style={styles.toastText}>{toastMessage}</RNText>
              <TouchableOpacity onPress={() => router.push('/cart')}>
                <RNText style={styles.toastLink}>View Cart →</RNText>
              </TouchableOpacity>
            </View>
          )}

          {/* Results Summary Subheader */}
          <View style={styles.resultsSubheader}>
            <Text variant="caption">
              {totalCount} {totalCount === 1 ? 'listing' : 'listings'} within {filters.radiusKm} km
              {filters.organicOnly ? ' · Organic only' : ''}
            </Text>
            <TouchableOpacity onPress={refresh} style={styles.refreshBtn}>
              <RNText style={styles.refreshBtnText}>🔄 Refresh</RNText>
            </TouchableOpacity>
          </View>

          {/* Content Area: List vs Map */}
          {isLoading ? (
            <View style={styles.centerLoading}>
              <ActivityIndicator size="large" color="#2d6a4f" />
              <Text variant="caption" style={{ marginTop: 10 }}>
                Finding produce near you...
              </Text>
            </View>
          ) : error ? (
            <View style={styles.centerError}>
              <RNText style={styles.errorEmoji}>⚠️</RNText>
              <Text variant="h2">Something went wrong</Text>
              <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
                {error}
              </Text>
              <Button label="Try Again" onPress={refresh} style={{ marginTop: 12 }} />
            </View>
          ) : listings.length === 0 ? (
            <View style={styles.centerEmpty}>
              <RNText style={styles.emptyEmoji}>🔍</RNText>
              <Text variant="h2">No produce found</Text>
              <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
                No active produce matching your filters within {filters.radiusKm} km of {userLocation.name}.
              </Text>
              <Button
                label="Reset Filters"
                variant="secondary"
                onPress={resetFilters}
                style={{ marginTop: 16 }}
              />
            </View>
          ) : viewMode === 'list' ? (
            <ScrollView
              style={styles.listScrollView}
              contentContainerStyle={styles.listContent}
              showsVerticalScrollIndicator={false}
            >
              {listings.map((item) => (
                <ListingCard
                  key={item.id}
                  listing={item}
                  onAddedToCart={handleAddedToCart}
                />
              ))}
            </ScrollView>
          ) : (
            <ProduceMapView
              listings={listings}
              userLocation={userLocation}
              onAddedToCart={handleAddedToCart}
            />
          )}
        </View>
      )}

      {/* LAND TAB CONTENT (Sprint 4 Preview) */}
      {activeTab === 'land' && (
        <ScrollView contentContainerStyle={styles.landTabContainer}>
          <Card style={styles.landCard}>
            <RNText style={styles.landHeroEmoji}>🌾</RNText>
            <Text variant="h1" style={{ textAlign: 'center' }}>
              Farmland Reservation
            </Text>
            <Text variant="body" style={styles.landSubtitle}>
              Reserve farm plots, select seasonal crops, track growth updates from farmers, and choose
              fresh harvest delivery or direct buy-back!
            </Text>

            <View style={styles.landFeatureBox}>
              <Text variant="label" style={{ fontWeight: '700' }}>
                Planned for Sprint 4 (V2 Core)
              </Text>
              <RNText style={styles.landFeatureItem}>
                • Interactive Plot Map Search with GeoJSON boundaries
              </RNText>
              <RNText style={styles.landFeatureItem}>
                • Season Selection & Online Reservation Agreement
              </RNText>
              <RNText style={styles.landFeatureItem}>
                • Crop Timeline & High-Res Inspection Progress
              </RNText>
              <RNText style={styles.landFeatureItem}>
                • Harvest Delivery or Guaranteed Buy-Back Option
              </RNText>
            </View>

            <Button
              label="← Back to Produce"
              onPress={() => setActiveTab('produce')}
              style={{ marginTop: 16 }}
            />
          </Card>
        </ScrollView>
      )}

      {/* Filter Modal Sheet */}
      <FilterSheet
        visible={filterSheetVisible}
        filters={filters}
        onApply={(updated) => setFilters(updated)}
        onReset={resetFilters}
        onClose={() => setFilterSheetVisible(false)}
      />

      {/* District / Location Picker Modal */}
      <Modal
        visible={locationPickerVisible}
        animationType="fade"
        transparent
        onRequestClose={() => setLocationPickerVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <Card style={styles.locationModal}>
            <Text variant="h2">Select Location</Text>
            <Text variant="caption" style={{ marginTop: 2, marginBottom: 14 }}>
              Choose a district to explore nearby fresh produce:
            </Text>
            {POPULAR_DISTRICTS.map((dist) => (
              <TouchableOpacity
                key={dist.name}
                style={[
                  styles.locationOption,
                  userLocation.name === dist.name && styles.locationOptionActive,
                ]}
                onPress={() => {
                  setUserLocation(dist);
                  setLocationPickerVisible(false);
                }}
              >
                <RNText style={styles.locationOptionText}>📍 {dist.name}</RNText>
                {userLocation.name === dist.name && (
                  <RNText style={styles.locationOptionCheck}>✓</RNText>
                )}
              </TouchableOpacity>
            ))}
            <Button
              label="Close"
              variant="secondary"
              onPress={() => setLocationPickerVisible(false)}
              style={{ marginTop: 12 }}
            />
          </Card>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f0',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 48,
    paddingBottom: 10,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#ebebe6',
  },
  headerLeft: {
    flex: 1,
  },
  appTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: '#1b4332',
  },
  locationPill: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 2,
    gap: 4,
  },
  locationPinIcon: {
    fontSize: 12,
  },
  locationText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#40916c',
  },
  locationArrow: {
    fontSize: 10,
    color: '#888',
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  cartButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#f0fdf4',
    borderWidth: 1,
    borderColor: '#bbf7d0',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  cartIcon: {
    fontSize: 18,
  },
  cartBadge: {
    position: 'absolute',
    top: -4,
    right: -4,
    backgroundColor: '#2d6a4f',
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 4,
  },
  cartBadgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: '800',
  },
  profileButton: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#f5f5f0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  profileIcon: {
    fontSize: 18,
  },
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e7eb',
  },
  tabItem: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    borderBottomWidth: 2.5,
    borderBottomColor: 'transparent',
  },
  tabItemActive: {
    borderBottomColor: '#2d6a4f',
  },
  tabItemText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#777',
  },
  tabItemTextActive: {
    color: '#2d6a4f',
    fontWeight: '700',
  },
  searchSection: {
    flexDirection: 'row',
    paddingHorizontal: 14,
    paddingTop: 10,
    gap: 8,
    alignItems: 'center',
  },
  searchBar: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 10,
    paddingHorizontal: 10,
    height: 40,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  searchIcon: {
    fontSize: 14,
    marginRight: 6,
  },
  searchInput: {
    flex: 1,
    fontSize: 13,
    color: '#1b1b1b',
    padding: 0,
  },
  clearBtn: {
    padding: 4,
  },
  clearBtnText: {
    fontSize: 12,
    color: '#999',
    fontWeight: '700',
  },
  filterTrigger: {
    paddingHorizontal: 10,
    height: 40,
    borderRadius: 10,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e5e7eb',
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterTriggerActive: {
    backgroundColor: '#e6ede8',
    borderColor: '#2d6a4f',
  },
  filterTriggerText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#2d6a4f',
  },
  viewModeToggle: {
    paddingHorizontal: 10,
    height: 40,
    borderRadius: 10,
    backgroundColor: '#2d6a4f',
    alignItems: 'center',
    justifyContent: 'center',
  },
  viewModeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '700',
  },
  cropChipsWrapper: {
    paddingVertical: 8,
  },
  cropChipsScroll: {
    paddingHorizontal: 14,
    gap: 8,
  },
  cropChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  cropChipSelected: {
    backgroundColor: '#2d6a4f',
    borderColor: '#2d6a4f',
  },
  cropEmoji: {
    fontSize: 13,
  },
  cropChipLabel: {
    fontSize: 12,
    color: '#555',
    fontWeight: '500',
  },
  cropChipLabelSelected: {
    color: '#fff',
    fontWeight: '700',
  },
  toastBanner: {
    marginHorizontal: 14,
    marginBottom: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#dcfce7',
    borderWidth: 1,
    borderColor: '#86efac',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
  },
  toastText: {
    color: '#166534',
    fontSize: 12,
    fontWeight: '600',
  },
  toastLink: {
    color: '#1b4332',
    fontSize: 12,
    fontWeight: '800',
    textDecorationLine: 'underline',
  },
  resultsSubheader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingBottom: 6,
  },
  refreshBtn: {
    padding: 2,
  },
  refreshBtnText: {
    fontSize: 11,
    color: '#2d6a4f',
    fontWeight: '600',
  },
  listScrollView: {
    flex: 1,
  },
  listContent: {
    paddingHorizontal: 14,
    paddingBottom: 20,
  },
  centerLoading: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  centerError: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  errorEmoji: {
    fontSize: 40,
    marginBottom: 8,
  },
  centerEmpty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  emptyEmoji: {
    fontSize: 48,
    marginBottom: 8,
  },
  landTabContainer: {
    padding: 20,
    alignItems: 'center',
  },
  landCard: {
    width: '100%',
    maxWidth: 420,
    alignItems: 'center',
    padding: 20,
    gap: 12,
  },
  landHeroEmoji: {
    fontSize: 56,
  },
  landSubtitle: {
    textAlign: 'center',
    color: '#555',
    lineHeight: 20,
  },
  landFeatureBox: {
    width: '100%',
    backgroundColor: '#f8fafc',
    borderRadius: 10,
    padding: 14,
    gap: 6,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  landFeatureItem: {
    fontSize: 12,
    color: '#475569',
    lineHeight: 18,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  locationModal: {
    width: '100%',
    maxWidth: 360,
    padding: 18,
  },
  locationOption: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f1ee',
  },
  locationOptionActive: {
    backgroundColor: '#f0fdf4',
    borderRadius: 6,
  },
  locationOptionText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  locationOptionCheck: {
    color: '#2d6a4f',
    fontWeight: '800',
    fontSize: 16,
  },
});
