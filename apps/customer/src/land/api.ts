import type { PlotItem, ReservationItem, CropTimelineItem } from './types.ts';
import { SEED_PLOTS, SEED_RESERVATIONS } from './mockData.ts';

function haversineDistanceM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000;
  const phi1 = (lat1 * Math.PI) / 180;
  const phi2 = (lat2 * Math.PI) / 180;
  const deltaPhi = ((lat2 - lat1) * Math.PI) / 180;
  const deltaLambda = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
    Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return Math.round(R * c);
}

// In-memory store for newly created reservations in current session
let localReservations: ReservationItem[] = [...SEED_RESERVATIONS];

/**
 * Fetch available plots near coordinates.
 */
export async function fetchPlots(lat: number, lng: number, radiusKm: number = 20): Promise<PlotItem[]> {
  try {
    const landModule: any = await import('../../../../packages/api/src/land.ts').catch(() => null);
    if (landModule && typeof landModule.searchPlots === 'function') {
      const data = await landModule.searchPlots({ lat, lng, radius_km: radiusKm });
      if (Array.isArray(data) && data.length > 0) {
        return data.map((p: any) => {
          const coords = p.centroid?.coordinates ?? [lng, lat];
          const distanceM = haversineDistanceM(lat, lng, coords[1], coords[0]);
          return {
            id: p.id,
            farmerId: p.farmerId,
            farmerName: 'Farmer Partner',
            title: p.title,
            description: p.description,
            areaAcres: p.areaAcres,
            soilType: p.soilType || 'Loam',
            waterSource: p.waterSource || 'Borewell',
            status: p.status || 'AVAILABLE',
            village: 'Local Village',
            district: 'Local District',
            distanceM,
            centroid: [coords[0], coords[1]],
            boundary: p.boundary?.coordinates || [],
            seasons: (p.seasons || []).map((s: any) => ({
              id: s.id,
              plotId: s.plotId,
              season: s.season,
              startDate: s.startDate,
              endDate: s.endDate,
              pricePerKg: s.price,
              allowedCrops: s.allowedCrops || [],
            })),
          };
        });
      }
    }
  } catch {
    // Graceful fallback to seed dataset
  }

  // Fallback to seed plots with recomputed distance
  return SEED_PLOTS.map((plot) => {
    const [pLng, pLat] = plot.centroid;
    const distanceM = haversineDistanceM(lat, lng, pLat, pLng);
    return {
      ...plot,
      distanceM,
    };
  });
}

/**
 * Fetch a single plot by ID.
 */
export async function fetchPlotDetail(plotId: string): Promise<PlotItem> {
  const plots = await fetchPlots(21.1904, 81.2849, 100);
  const found = plots.find((p) => p.id === plotId);
  if (found) return found;
  throw new Error(`Plot ${plotId} not found`);
}

/**
 * Fetch customer's reservations.
 */
export async function fetchCustomerReservations(): Promise<ReservationItem[]> {
  try {
    const landModule: any = await import('../../../../packages/api/src/land.ts').catch(() => null);
    if (landModule && typeof landModule.getMyReservations === 'function') {
      const data = await landModule.getMyReservations();
      if (Array.isArray(data) && data.length > 0) {
        // Map backend reservations
        return data.map((r: any) => ({
          id: r.id,
          plotId: r.plotSeasonId,
          plotTitle: r.plotTitle || 'Reserved Farmland Plot',
          plotSeasonId: r.plotSeasonId,
          seasonName: r.seasonName || 'Active Season',
          farmerId: r.farmerId,
          farmerName: 'Assigned Farmer',
          cropId: r.cropId,
          cropName: r.cropId.toUpperCase(),
          expectedQtyKg: r.expectedQtyKg,
          status: r.status,
          deposit: r.deposit || 0,
          total: r.total || 0,
          createdAt: r.createdAt || new Date().toISOString().slice(0, 10),
          updates: [],
        }));
      }
    }
  } catch {
    // Fallback to local reservations
  }

  return localReservations;
}

/**
 * Submit a new plot reservation.
 */
export async function submitPlotReservation(payload: {
  plot: PlotItem;
  season: { id: string; season: string; pricePerKg: number };
  cropId: string;
  expectedQtyKg: number;
}): Promise<ReservationItem> {
  const { plot, season, cropId, expectedQtyKg } = payload;
  const total = Math.round(season.pricePerKg * expectedQtyKg * 100) / 100;
  const deposit = Math.round(total * 0.2 * 100) / 100;

  try {
    const landModule: any = await import('../../../../packages/api/src/land.ts').catch(() => null);
    if (landModule && typeof landModule.createReservation === 'function') {
      await landModule.createReservation({
        plotSeasonId: season.id,
        cropId,
        expectedQtyKg,
      });
    }
  } catch {
    // Simulated offline creation
  }

  const newReservation: ReservationItem = {
    id: `res-${Date.now().toString().slice(-6)}`,
    plotId: plot.id,
    plotTitle: plot.title,
    plotSeasonId: season.id,
    seasonName: season.season,
    farmerId: plot.farmerId,
    farmerName: plot.farmerName,
    farmerPhone: plot.farmerPhone,
    cropId,
    cropName: cropId.charAt(0).toUpperCase() + cropId.slice(1),
    expectedQtyKg,
    status: 'PENDING',
    deposit,
    total,
    createdAt: new Date().toISOString().slice(0, 10),
    updates: [
      {
        id: `upd-init-${Date.now()}`,
        stage: 'SOWN',
        stageTitle: 'Reservation Submitted & Sowing Prep',
        note: `Reservation requested for ${expectedQtyKg} kg of ${cropId}. Farmer will accept terms and begin field preparation.`,
        createdAt: new Date().toISOString().slice(0, 10),
        photos: [],
      },
    ],
  };

  localReservations = [newReservation, ...localReservations];
  return newReservation;
}

/**
 * Fetch crop timeline updates for a reservation.
 */
export async function fetchReservationTimeline(reservationId: string): Promise<CropTimelineItem[]> {
  const res = localReservations.find((r) => r.id === reservationId);
  return res?.updates || [];
}
