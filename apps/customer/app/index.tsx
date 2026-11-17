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
import {
  useLand,
  PlotCard,
  PlotMapView,
  ReservationModal,
  CropTimelineView,
  MyReservationsList,
} from '../src/land/index.ts';

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

const SEASONS_LIST = [
  { id: 'ALL', label: 'All Seasons' },
  { id: 'Rabi 2026-27', label: 'Rabi 2026-27' },
  { id: 'Kharif 2026', label: 'Kharif 2026' },
  { id: 'Zaid 2027', label: 'Zaid 2027' },
];

export default function CustomerHomeScreen() {
  const router = useRouter();
  const { isAuthenticated, accessToken } = useAuth();
  usePushTokenRegistration(isAuthenticated ? accessToken : null);
  const cart = useCart();

  const [activeTab, setActiveTab] = useState<'produce' | 'land'>('produce');
  const [filterSheetVisible, setFilterSheetVisible] = useState(false);
  const [locationPickerVisible, setLocationPickerVisible] = useState(false);

  // Produce State (Sprint 2)
  const {
    userLocation,
    setUserLocation,
    filters: produceFilters,
    setFilters: setProduceFilters,
    setCropFilter,
    resetFilters: resetProduceFilters,
    viewMode: produceViewMode,
    setViewMode: setProduceViewMode,
    listings,
    totalCount: produceTotalCount,
    isLoading: produceLoading,
    error: produceError,
    refresh: refreshProduce,
    toastMessage: produceToast,
    handleAddedToCart,
  } = useProduceBrowse();

  // Land State (Sprint 4)
  const {
    plots,
    reservations,
    filters: landFilters,
    setFilters: setLandFilters,
    viewMode: landViewMode,
    setViewMode: setLandViewMode,
    subTab: landSubTab,
    setSubTab: setLandSubTab,
    isLoading: landLoading,
    error: landError,
    refresh: refreshLand,
    selectedPlotForReservation,
    setSelectedPlotForReservation,
    activeTimelineReservation,
    setActiveTimelineReservation,
    handleCreateReservation,
    toastMessage: landToast,
  } = useLand(userLocation);

  const cartItemCount = cart.items.reduce((sum, item) => sum + item.qty, 0);

  return (
    <View style={styles.container}>
      {/* Top Header Bar */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text variant="h1" style={styles.appTitle}>
            {activeTab === 'produce' ? '🥕 Farm Fresh' : '🌾 Farm Land'}
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

      {/* ================= PRODUCE TAB (Sprint 2) ================= */}
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
                value={produceFilters.query}
                onChangeText={(text) => setProduceFilters((prev) => ({ ...prev, query: text }))}
              />
              {produceFilters.query ? (
                <TouchableOpacity
                  onPress={() => setProduceFilters((prev) => ({ ...prev, query: '' }))}
                  style={styles.clearBtn}
                >
                  <RNText style={styles.clearBtnText}>✕</RNText>
                </TouchableOpacity>
              ) : null}
            </View>

            {/* Filter Trigger */}
            <TouchableOpacity
              style={styles.filterTrigger}
              onPress={() => setFilterSheetVisible(true)}
            >
              <RNText style={styles.filterTriggerText}>⚙ Filter</RNText>
            </TouchableOpacity>

            {/* View Mode Toggle (List vs Map) */}
            <TouchableOpacity
              style={styles.viewModeToggle}
              onPress={() =>
                setProduceViewMode((prev) => (prev === 'list' ? 'map' : 'list'))
              }
            >
              <RNText style={styles.viewModeText}>
                {produceViewMode === 'list' ? '🗺 Map' : '📋 List'}
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
                  (crop.id === 'ALL' && !produceFilters.crop) || produceFilters.crop === crop.id;
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

          {/* Toast Banner */}
          {produceToast && (
            <View style={styles.toastBanner}>
              <RNText style={styles.toastText}>{produceToast}</RNText>
              <TouchableOpacity onPress={() => router.push('/cart')}>
                <RNText style={styles.toastLink}>View Cart →</RNText>
              </TouchableOpacity>
            </View>
          )}

          {/* Results Summary Subheader */}
          <View style={styles.resultsSubheader}>
            <Text variant="caption">
              {produceTotalCount} {produceTotalCount === 1 ? 'listing' : 'listings'} within {produceFilters.radiusKm} km
            </Text>
            <TouchableOpacity onPress={refreshProduce} style={styles.refreshBtn}>
              <RNText style={styles.refreshBtnText}>🔄 Refresh</RNText>
            </TouchableOpacity>
          </View>

          {/* Content Area */}
          {produceLoading ? (
            <View style={styles.centerLoading}>
              <ActivityIndicator size="large" color="#2d6a4f" />
              <Text variant="caption" style={{ marginTop: 10 }}>
                Finding produce near you...
              </Text>
            </View>
          ) : produceError ? (
            <View style={styles.centerError}>
              <RNText style={styles.errorEmoji}>⚠️</RNText>
              <Text variant="h2">Something went wrong</Text>
              <Text variant="caption">{produceError}</Text>
              <Button label="Try Again" onPress={refreshProduce} style={{ marginTop: 12 }} />
            </View>
          ) : listings.length === 0 ? (
            <View style={styles.centerEmpty}>
              <RNText style={styles.emptyEmoji}>🔍</RNText>
              <Text variant="h2">No produce found</Text>
              <Button label="Reset Filters" variant="secondary" onPress={resetProduceFilters} style={{ marginTop: 14 }} />
            </View>
          ) : produceViewMode === 'list' ? (
            <ScrollView style={styles.listScrollView} contentContainerStyle={styles.listContent}>
              {listings.map((item) => (
                <ListingCard key={item.id} listing={item} onAddedToCart={handleAddedToCart} />
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

      {/* ================= LAND TAB (Sprint 4: Plots & Crop Timeline) ================= */}
      {activeTab === 'land' && (
        <View style={{ flex: 1 }}>
          {/* Land Sub-Tabs Header */}
          <View style={styles.landSubTabsBar}>
            <TouchableOpacity
              style={[styles.landSubTab, landSubTab === 'explore' && styles.landSubTabActive]}
              onPress={() => {
                setLandSubTab('explore');
                setActiveTimelineReservation(null);
              }}
            >
              <RNText
                style={[
                  styles.landSubTabText,
                  landSubTab === 'explore' && styles.landSubTabTextActive,
                ]}
              >
                🗺 Explore Plots ({plots.length})
              </RNText>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.landSubTab, landSubTab === 'my_reservations' && styles.landSubTabActive]}
              onPress={() => setLandSubTab('my_reservations')}
            >
              <RNText
                style={[
                  styles.landSubTabText,
                  landSubTab === 'my_reservations' && styles.landSubTabTextActive,
                ]}
              >
                🌱 My Reservations ({reservations.length})
              </RNText>
            </TouchableOpacity>
          </View>

          {/* Toast Notification */}
          {landToast && (
            <View style={styles.toastBanner}>
              <RNText style={styles.toastText}>{landToast}</RNText>
            </View>
          )}

          {/* SUBTAB 1: EXPLORE PLOTS */}
          {landSubTab === 'explore' && (
            <View style={{ flex: 1 }}>
              {/* Search & View Mode Switcher */}
              <View style={styles.searchSection}>
                <View style={styles.searchBar}>
                  <RNText style={styles.searchIcon}>🔍</RNText>
                  <TextInput
                    style={styles.searchInput}
                    placeholder="Search plot title, soil, or crops..."
                    placeholderTextColor="#999"
                    value={landFilters.query}
                    onChangeText={(text) => setLandFilters((prev) => ({ ...prev, query: text }))}
                  />
                  {landFilters.query ? (
                    <TouchableOpacity
                      onPress={() => setLandFilters((prev) => ({ ...prev, query: '' }))}
                      style={styles.clearBtn}
                    >
                      <RNText style={styles.clearBtnText}>✕</RNText>
                    </TouchableOpacity>
                  ) : null}
                </View>

                {/* View Mode Toggle (Map vs List) */}
                <TouchableOpacity
                  style={styles.viewModeToggle}
                  onPress={() =>
                    setLandViewMode((prev) => (prev === 'map' ? 'list' : 'map'))
                  }
                >
                  <RNText style={styles.viewModeText}>
                    {landViewMode === 'map' ? '📋 List' : '🗺 Map'}
                  </RNText>
                </TouchableOpacity>
              </View>

              {/* Season Filter Chips */}
              <View style={styles.cropChipsWrapper}>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={styles.cropChipsScroll}
                >
                  {SEASONS_LIST.map((season) => {
                    const isSelected =
                      (season.id === 'ALL' && !landFilters.season) ||
                      landFilters.season === season.id;
                    return (
                      <TouchableOpacity
                        key={season.id}
                        style={[styles.cropChip, isSelected && styles.cropChipSelected]}
                        onPress={() =>
                          setLandFilters((prev) => ({
                            ...prev,
                            season: season.id === 'ALL' ? null : season.id,
                          }))
                        }
                      >
                        <RNText
                          style={[
                            styles.cropChipLabel,
                            isSelected && styles.cropChipLabelSelected,
                          ]}
                        >
                          🗓 {season.label}
                        </RNText>
                      </TouchableOpacity>
                    );
                  })}
                </ScrollView>
              </View>

              {/* Content Area */}
              {landLoading ? (
                <View style={styles.centerLoading}>
                  <ActivityIndicator size="large" color="#0f766e" />
                  <Text variant="caption" style={{ marginTop: 10 }}>
                    Finding agricultural plots near you...
                  </Text>
                </View>
              ) : landError ? (
                <View style={styles.centerError}>
                  <RNText style={styles.errorEmoji}>⚠️</RNText>
                  <Text variant="h2">Unable to load plots</Text>
                  <Text variant="caption">{landError}</Text>
                  <Button label="Try Again" onPress={refreshLand} style={{ marginTop: 12 }} />
                </View>
              ) : plots.length === 0 ? (
                <View style={styles.centerEmpty}>
                  <RNText style={styles.emptyEmoji}>🌾</RNText>
                  <Text variant="h2">No plots match filters</Text>
                  <Button
                    label="Reset Filters"
                    variant="secondary"
                    onPress={() =>
                      setLandFilters({
                        query: '',
                        season: null,
                        crop: null,
                        soilType: null,
                        radiusKm: 25,
                        minAcres: null,
                        maxAcres: null,
                        sort: 'distance',
                      })
                    }
                    style={{ marginTop: 14 }}
                  />
                </View>
              ) : landViewMode === 'map' ? (
                <PlotMapView
                  plots={plots}
                  userLocation={userLocation}
                  onReserve={(plot) => setSelectedPlotForReservation(plot)}
                />
              ) : (
                <ScrollView style={styles.listScrollView} contentContainerStyle={styles.listContent}>
                  {plots.map((plot) => (
                    <PlotCard
                      key={plot.id}
                      plot={plot}
                      onReserve={(p) => setSelectedPlotForReservation(p)}
                    />
                  ))}
                </ScrollView>
              )}
            </View>
          )}

          {/* SUBTAB 2: MY RESERVATIONS & CROP TIMELINE */}
          {landSubTab === 'my_reservations' && (
            <View style={{ flex: 1 }}>
              {activeTimelineReservation ? (
                <CropTimelineView
                  reservation={activeTimelineReservation}
                  onClose={() => setActiveTimelineReservation(null)}
                />
              ) : (
                <ScrollView style={styles.listScrollView} contentContainerStyle={styles.listContent}>
                  <MyReservationsList
                    reservations={reservations}
                    onSelectReservation={(res) => setActiveTimelineReservation(res)}
                    onExplorePlots={() => setLandSubTab('explore')}
                  />
                </ScrollView>
              )}
            </View>
          )}
        </View>
      )}

      {/* Produce Filter Modal Sheet */}
      <FilterSheet
        visible={filterSheetVisible}
        filters={produceFilters}
        onApply={(updated) => setProduceFilters(updated)}
        onReset={resetProduceFilters}
        onClose={() => setFilterSheetVisible(false)}
      />

      {/* Plot Reservation Modal */}
      <ReservationModal
        visible={Boolean(selectedPlotForReservation)}
        plot={selectedPlotForReservation}
        onConfirm={handleCreateReservation}
        onClose={() => setSelectedPlotForReservation(null)}
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
              Choose a district to explore nearby produce & plots:
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
  landSubTabsBar: {
    flexDirection: 'row',
    backgroundColor: '#f8fafc',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
    gap: 8,
  },
  landSubTab: {
    flex: 1,
    paddingVertical: 8,
    alignItems: 'center',
    borderRadius: 8,
    backgroundColor: '#f1f5f9',
  },
  landSubTabActive: {
    backgroundColor: '#0f766e',
  },
  landSubTabText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#475569',
  },
  landSubTabTextActive: {
    color: '#fff',
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
