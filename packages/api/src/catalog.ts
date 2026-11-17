import { client } from './client';

/** The generated schema is still a stub for catalog, so these calls go through an untyped GET. */
const rawGet = client.GET as unknown as (
  path: string,
  init?: unknown,
) => Promise<{ data?: unknown; error?: unknown; response: Response }>;

export type Grade = 'A' | 'B' | 'C';
export type Unit = 'kg' | 'quintal' | 'dozen' | 'piece' | 'bunch' | 'litre';
export type SearchSort = 'distance' | 'price' | 'newest';
export type ListingStatus = 'ACTIVE' | 'PAUSED' | 'SOLD_OUT' | 'ARCHIVED';

export type GeoPoint = {
  type: 'Point';
  /** [longitude, latitude] */
  coordinates: [number, number];
};

export interface ListingSearchProperties {
  id: string;
  farmId: string;
  farmName: string;
  farmerId: string;
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
  mediaIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface ListingFeature {
  type: 'Feature';
  geometry: GeoPoint;
  properties: ListingSearchProperties;
}

export interface ListingFeatureCollection {
  type: 'FeatureCollection';
  features: ListingFeature[];
  cursor?: string | null;
}

export interface ListingOut {
  id: string;
  farmId: string;
  farmName: string;
  farmerId: string;
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
  district: string;
  village?: string | null;
  pickupLocation: GeoPoint;
  status: ListingStatus;
  mediaIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface MediaOut {
  id: string;
  ownerType: string;
  ownerId: string;
  ownerService: string;
  uploadedBy: string;
  status: 'PENDING_UPLOAD' | 'PROCESSING' | 'READY' | 'REJECTED' | 'FAILED' | 'DELETED';
  contentType: string;
  sizeBytes: number;
  width?: number | null;
  height?: number | null;
  url?: string | null;
  thumbUrl?: string | null;
  exifTime?: string | null;
  isGeoVerified: boolean;
  geoStatus: 'PENDING' | 'VERIFIED' | 'TOO_FAR' | 'NO_GPS' | 'NO_REFERENCE' | 'NOT_APPLICABLE';
  distanceM?: number | null;
  rejectReason?: string | null;
  createdAt: string;
  processedAt?: string | null;
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

/** Search produce listings near coordinates. */
export async function searchListings(params: ListingSearchParams): Promise<ListingFeatureCollection> {
  const query = new URLSearchParams();
  query.set('lat', String(params.lat));
  query.set('lng', String(params.lng));
  if (params.radius_km !== undefined) query.set('radius_km', String(params.radius_km));
  if (params.crop) query.set('crop', params.crop);
  if (params.min_price !== undefined) query.set('min_price', String(params.min_price));
  if (params.max_price !== undefined) query.set('max_price', String(params.max_price));
  if (params.grade) query.set('grade', params.grade);
  if (params.organic !== undefined) query.set('organic', String(params.organic));
  if (params.sort) query.set('sort', params.sort);
  if (params.limit !== undefined) query.set('limit', String(params.limit));
  if (params.cursor) query.set('cursor', params.cursor);

  const { data, error, response } = await rawGet(
    `/catalog/listings/search?${query.toString()}`,
  );

  if (error || !data) {
    throw new Error(`Failed to search listings: ${response.status}`);
  }
  return data as unknown as ListingFeatureCollection;
}

/** Get full listing detail by listing ID. */
export async function getListing(listingId: string): Promise<ListingOut> {
  const { data, error, response } = await rawGet(
    `/catalog/listings/${listingId}`,
  );

  if (error || !data) {
    throw new Error(`Failed to get listing ${listingId}: ${response.status}`);
  }
  return data as unknown as ListingOut;
}

/** Get media details by media ID. */
export async function getMedia(mediaId: string): Promise<MediaOut> {
  const { data, error, response } = await rawGet(
    `/media/${mediaId}`,
  );

  if (error || !data) {
    throw new Error(`Failed to get media ${mediaId}: ${response.status}`);
  }
  return data as unknown as MediaOut;
}
