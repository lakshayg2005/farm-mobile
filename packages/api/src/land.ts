import { client } from './client';

export type SoilType = 'Clay' | 'Clay Loam' | 'Sandy Loam' | 'Black Cotton' | 'Alluvial' | 'Red Soil';
export type WaterSource = 'Canal' | 'Borewell' | 'Drip Irrigation' | 'Canal + Tube Well' | 'Rainfed';
export type ReservationStatus = 'PENDING' | 'ACCEPTED' | 'ACTIVE' | 'HARVEST_READY' | 'COMPLETED' | 'CANCELLED' | 'REJECTED';
export type CropStage = 'SOWN' | 'GROWING' | 'FLOWERING' | 'HARVEST_READY';

export interface PolygonGeometry {
  type: 'Polygon';
  coordinates: number[][][];
}

export interface PointGeometry {
  type: 'Point';
  coordinates: [number, number]; // [lng, lat]
}

export interface SeasonOut {
  id: string;
  plotId: string;
  season: string;
  startDate: string;
  endDate: string;
  price: number;
  allowedCrops: string[];
}

export interface PlotOut {
  id: string;
  farmerId: string;
  title: string;
  description?: string | null;
  areaAcres: number;
  soilType?: string | null;
  waterSource?: string | null;
  status: string;
  boundary: PolygonGeometry;
  centroid: PointGeometry;
  createdAt: string;
  seasons?: SeasonOut[];
}

export interface ReservationCreate {
  plotSeasonId: string;
  cropId: string;
  expectedQtyKg: number;
}

export interface ReservationOut {
  id: string;
  plotSeasonId: string;
  farmerId: string;
  customerId: string;
  cropId: string;
  expectedQtyKg: number;
  status: ReservationStatus;
  deposit?: number | null;
  total?: number | null;
  expiresAt?: string | null;
  createdAt?: string;
  plotTitle?: string;
  seasonName?: string;
}

export interface CropUpdateOut {
  id: string;
  reservationId: string;
  stage: CropStage;
  note?: string | null;
  mediaIds: string[];
  location?: PointGeometry | null;
  createdAt: string;
  photos?: Array<{
    id: string;
    url: string;
    thumbUrl: string;
    isGeoVerified: boolean;
    distanceM?: number | null;
  }>;
}

export interface PlotSearchParams {
  lat?: number;
  lng?: number;
  radius_km?: number;
  season?: string;
  crop?: string;
  limit?: number;
  offset?: number;
}

/** Search plots near a point or viewport */
export async function searchPlots(params: PlotSearchParams): Promise<PlotOut[]> {
  const query = new URLSearchParams();
  if (params.lat !== undefined) query.set('lat', String(params.lat));
  if (params.lng !== undefined) query.set('lng', String(params.lng));
  if (params.radius_km !== undefined) query.set('radius_km', String(params.radius_km));
  if (params.season) query.set('season', params.season);
  if (params.crop) query.set('crop', params.crop);
  if (params.limit !== undefined) query.set('limit', String(params.limit));
  if (params.offset !== undefined) query.set('offset', String(params.offset));

  const { data, error, response } = await client.GET(
    (`/land/plots/search?${query.toString()}`) as unknown as '/land/plots/search',
  );

  if (error || !data) {
    throw new Error(`Failed to search plots: ${response.status}`);
  }
  return data as unknown as PlotOut[];
}

/** Get plot details by ID */
export async function getPlot(plotId: string): Promise<PlotOut> {
  const { data, error, response } = await client.GET(
    (`/land/plots/${plotId}`) as unknown as '/land/plots/{plot_id}',
    {
      params: { path: { plot_id: plotId } },
    } as any,
  );

  if (error || !data) {
    throw new Error(`Failed to get plot ${plotId}: ${response.status}`);
  }
  return data as unknown as PlotOut;
}

/** Create a plot reservation */
export async function createReservation(body: ReservationCreate): Promise<ReservationOut> {
  const { data, error, response } = await client.POST('/land/reservations' as any, {
    body,
  } as any);

  if (error || !data) {
    throw new Error(`Failed to create reservation: ${response.status}`);
  }
  return data as unknown as ReservationOut;
}

/** Get current customer's reservations */
export async function getMyReservations(): Promise<ReservationOut[]> {
  const { data, error, response } = await client.GET('/land/reservations/mine' as any);
  if (error || !data) {
    throw new Error(`Failed to get my reservations: ${response.status}`);
  }
  return data as unknown as ReservationOut[];
}

/** Get crop updates timeline for a reservation */
export async function getCropUpdates(reservationId: string): Promise<CropUpdateOut[]> {
  const { data, error, response } = await client.GET(
    (`/land/reservations/${reservationId}/updates`) as unknown as '/land/reservations/{reservation_id}/updates',
    {
      params: { path: { reservation_id: reservationId } },
    } as any,
  );

  if (error || !data) {
    throw new Error(`Failed to get crop updates: ${response.status}`);
  }
  return data as unknown as CropUpdateOut[];
}

/** Cancel a pending reservation */
export async function cancelReservation(reservationId: string): Promise<ReservationOut> {
  const { data, error, response } = await client.POST(
    (`/land/reservations/${reservationId}/cancel`) as unknown as '/land/reservations/{reservation_id}/cancel',
    {
      params: { path: { reservation_id: reservationId } },
    } as any,
  );

  if (error || !data) {
    throw new Error(`Failed to cancel reservation: ${response.status}`);
  }
  return data as unknown as ReservationOut;
}
