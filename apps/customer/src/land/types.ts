export type SoilType =
  | 'Clay'
  | 'Clay Loam'
  | 'Sandy Loam'
  | 'Black Cotton'
  | 'Alluvial'
  | 'Red Loam';

export type WaterSource =
  | 'Canal'
  | 'Borewell'
  | 'Drip Irrigation'
  | 'Canal + Tube Well'
  | 'Rainfed';

export type ReservationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'ACTIVE'
  | 'HARVEST_READY'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED';

export type CropStage = 'SOWN' | 'GROWING' | 'FLOWERING' | 'HARVEST_READY';

export interface PlotSeasonItem {
  id: string;
  plotId: string;
  season: string; // e.g. "Kharif 2026", "Rabi 2026", "Zaid 2027"
  startDate: string;
  endDate: string;
  pricePerKg: number;
  allowedCrops: string[];
}

export interface PlotItem {
  id: string;
  farmerId: string;
  farmerName: string;
  farmerPhone?: string;
  title: string;
  description?: string | null;
  areaAcres: number;
  soilType: SoilType | string;
  waterSource: WaterSource | string;
  status: string;
  village: string;
  district: string;
  distanceM: number;
  centroid: [number, number]; // [lng, lat]
  boundary: number[][][]; // GeoJSON Polygon coordinates
  seasons: PlotSeasonItem[];
}

export interface CropTimelineItem {
  id: string;
  stage: CropStage;
  stageTitle: string;
  note?: string | null;
  createdAt: string;
  photos: Array<{
    id: string;
    url: string;
    thumbUrl: string;
    isGeoVerified: boolean;
    distanceM?: number | null;
  }>;
}

export interface ReservationItem {
  id: string;
  plotId: string;
  plotTitle: string;
  plotSeasonId: string;
  seasonName: string;
  farmerId: string;
  farmerName: string;
  farmerPhone?: string;
  cropId: string;
  cropName: string;
  expectedQtyKg: number;
  status: ReservationStatus;
  deposit: number;
  total: number;
  createdAt: string;
  updates: CropTimelineItem[];
}

export type LandSortOption = 'distance' | 'acres_desc' | 'acres_asc' | 'price_asc' | 'price_desc';

export interface LandFilterState {
  query: string;
  season: string | null;
  crop: string | null;
  soilType: string | null;
  radiusKm: number;
  minAcres: number | null;
  maxAcres: number | null;
  sort: LandSortOption;
}
