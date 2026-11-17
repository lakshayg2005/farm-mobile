import { useState, useEffect, useMemo, useCallback } from 'react';
import { fetchNearbyProduce } from './api.ts';
import { filterListings, sortListings } from './filterLogic.ts';
import type { ProduceListing, FilterState, LocationCoords } from './types.ts';

export const POPULAR_DISTRICTS: LocationCoords[] = [
  { name: 'Durg, CG', latitude: 21.1904, longitude: 81.2849 },
  { name: 'Raipur, CG', latitude: 21.2514, longitude: 81.6296 },
  { name: 'Bilaspur, CG', latitude: 22.0797, longitude: 82.1409 },
  { name: 'Nashik, MH', latitude: 19.9975, longitude: 73.7898 },
  { name: 'Pune, MH', latitude: 18.5204, longitude: 73.8567 },
  { name: 'Jaipur, RJ', latitude: 26.9124, longitude: 75.7873 },
];

export const INITIAL_FILTERS: FilterState = {
  query: '',
  crop: null,
  radiusKm: 15,
  organicOnly: false,
  minGrade: 'ALL',
  minPrice: null,
  maxPrice: null,
  sort: 'distance',
};

export function useProduceBrowse() {
  const [userLocation, setUserLocation] = useState<LocationCoords>(POPULAR_DISTRICTS[0]);
  const [filters, setFilters] = useState<FilterState>(INITIAL_FILTERS);
  const [viewMode, setViewMode] = useState<'list' | 'map'>('list');
  const [listings, setListings] = useState<ProduceListing[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const loadListings = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await fetchNearbyProduce({
        lat: userLocation.latitude,
        lng: userLocation.longitude,
        radius_km: filters.radiusKm,
        crop: filters.crop || undefined,
        organic: filters.organicOnly ? true : undefined,
      });
      setListings(data);
    } catch (err: unknown) {
      setError(err instanceof Error && err.message ? err.message : 'Failed to load produce listings');
    } finally {
      setIsLoading(false);
    }
  }, [userLocation.latitude, userLocation.longitude, filters.radiusKm, filters.crop, filters.organicOnly]);

  useEffect(() => {
    loadListings();
  }, [loadListings]);

  // Client-side filtering & sorting
  const filteredListings = useMemo(() => {
    const matched = filterListings(listings, filters);
    return sortListings(matched, filters.sort);
  }, [listings, filters]);

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage(null);
    }, 3000);
  }, []);

  const handleAddedToCart = useCallback(
    (item: ProduceListing) => {
      showToast(`Added 1 ${item.unit} ${item.cropName} to cart 🛒`);
    },
    [showToast],
  );

  const resetFilters = useCallback(() => {
    setFilters(INITIAL_FILTERS);
  }, []);

  const setCropFilter = useCallback((cropId: string | null) => {
    setFilters((prev) => ({
      ...prev,
      crop: prev.crop === cropId ? null : cropId,
    }));
  }, []);

  return {
    userLocation,
    setUserLocation,
    filters,
    setFilters,
    setCropFilter,
    resetFilters,
    viewMode,
    setViewMode,
    listings: filteredListings,
    totalCount: filteredListings.length,
    isLoading,
    error,
    refresh: loadListings,
    toastMessage,
    handleAddedToCart,
  };
}
