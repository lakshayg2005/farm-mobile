export type Grade = 'A' | 'B' | 'C';
export type Unit = 'kg' | 'quintal' | 'dozen' | 'piece' | 'bunch' | 'litre';
export type SearchSort = 'distance' | 'price' | 'newest';
export type ListingStatus = 'ACTIVE' | 'PAUSED' | 'SOLD_OUT' | 'ARCHIVED';
export type GeoStatus =
  | 'PENDING'
  | 'VERIFIED'
  | 'TOO_FAR'
  | 'NO_GPS'
  | 'NO_REFERENCE'
  | 'NOT_APPLICABLE';

export type GeoPoint = {
  type: 'Point';
  coordinates: [number, number]; // [lng, lat]
};

export interface ProducePhoto {
  id: string;
  url: string;
  thumbUrl: string;
  isGeoVerified: boolean;
  geoStatus: GeoStatus;
  distanceM?: number | null;
  rejectReason?: string | null;
}

export interface ProduceListing {
  id: string;
  farmId: string;
  farmName: string;
  farmerId: string;
  farmerName?: string;
  cropId: string;
  cropName: string;
  variety?: string | null;
  unit: Unit;
  pricePerUnit: number;
  qtyAvailable: number;
  harvestDate: string;
  grade?: Grade | null;
  isOrganic: boolean;
  description?: string | null;
  distanceM: number;
  district: string;
  village?: string | null;
  status: ListingStatus;
  pickupCoordinates: [number, number]; // [lng, lat]
  mediaIds: string[];
  photos: ProducePhoto[];
}

export type SortOption = 'distance' | 'price_asc' | 'price_desc' | 'harvest_date';

export interface FilterState {
  query: string;
  crop: string | null;
  radiusKm: number;
  organicOnly: boolean;
  minGrade: Grade | 'ALL';
  minPrice: number | null;
  maxPrice: number | null;
  sort: SortOption;
}

export interface LocationCoords {
  latitude: number;
  longitude: number;
  name: string;
}

export interface ListingSearchParams {
  lat: number;
  lng: number;
  radius_km?: number;
  crop?: string;
  min_price?: number;
  max_price?: number;
  grade?: Grade;
  organic?: boolean;
  sort?: SearchSort;
  limit?: number;
  cursor?: string;
}
