import { useState, useEffect, useMemo, useCallback } from 'react';
import { fetchPlots, fetchCustomerReservations, submitPlotReservation } from './api.ts';
import { filterPlots, sortPlots } from './filterLogic.ts';
import type { PlotItem, ReservationItem, LandFilterState, PlotSeasonItem } from './types.ts';
import type { LocationCoords } from '../browse/types.ts';

export const INITIAL_LAND_FILTERS: LandFilterState = {
  query: '',
  season: null,
  crop: null,
  soilType: null,
  radiusKm: 25,
  minAcres: null,
  maxAcres: null,
  sort: 'distance',
};

export function useLand(userLocation: LocationCoords) {
  const [plots, setPlots] = useState<PlotItem[]>([]);
  const [reservations, setReservations] = useState<ReservationItem[]>([]);
  const [filters, setFilters] = useState<LandFilterState>(INITIAL_LAND_FILTERS);
  const [viewMode, setViewMode] = useState<'map' | 'list'>('map');
  const [subTab, setSubTab] = useState<'explore' | 'my_reservations'>('explore');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Modals & Active Selections
  const [selectedPlotForReservation, setSelectedPlotForReservation] = useState<PlotItem | null>(null);
  const [activeTimelineReservation, setActiveTimelineReservation] = useState<ReservationItem | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [fetchedPlots, fetchedReservations] = await Promise.all([
        fetchPlots(userLocation.latitude, userLocation.longitude, filters.radiusKm),
        fetchCustomerReservations(),
      ]);
      setPlots(fetchedPlots);
      setReservations(fetchedReservations);
    } catch (err: unknown) {
      setError(err instanceof Error && err.message ? err.message : 'Failed to load land data');
    } finally {
      setIsLoading(false);
    }
  }, [userLocation.latitude, userLocation.longitude, filters.radiusKm]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const filteredPlots = useMemo(() => {
    const matched = filterPlots(plots, filters);
    return sortPlots(matched, filters.sort);
  }, [plots, filters]);

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage(null);
    }, 4000);
  }, []);

  const handleCreateReservation = useCallback(
    async (payload: {
      plot: PlotItem;
      season: PlotSeasonItem;
      cropId: string;
      expectedQtyKg: number;
    }) => {
      const created = await submitPlotReservation(payload);
      setReservations((prev) => [created, ...prev]);
      showToast(`✓ Reservation submitted for ${created.cropName}! Farmer notified.`);
      setSubTab('my_reservations');
      setActiveTimelineReservation(created);
    },
    [showToast],
  );

  return {
    plots: filteredPlots,
    allPlotsCount: plots.length,
    reservations,
    filters,
    setFilters,
    viewMode,
    setViewMode,
    subTab,
    setSubTab,
    isLoading,
    error,
    refresh: loadData,
    selectedPlotForReservation,
    setSelectedPlotForReservation,
    activeTimelineReservation,
    setActiveTimelineReservation,
    handleCreateReservation,
    toastMessage,
  };
}
