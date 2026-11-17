import type { ProduceListing, ProducePhoto, ListingSearchParams } from './types.ts';
import { SEED_PRODUCE_LISTINGS } from './mockData.ts';

type CatalogModule = typeof import('../../../../packages/api/src/catalog.ts');

/** Calculate haversine distance in metres between two (lat, lng) points. */
function haversineDistanceM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000; // radius of Earth in metres
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

/**
 * Fetch nearby produce from the catalog service (GET /catalog/listings/search).
 * Falls back to local seeds if the backend is unreachable or during tests/demo.
 */
export async function fetchNearbyProduce(params: ListingSearchParams): Promise<ProduceListing[]> {
  try {
    // Attempt dynamic API call if @farm/api or backend client is available
    const apiModule: CatalogModule | null = await import('../../../../packages/api/src/catalog.ts').catch(() => null);
    if (apiModule && typeof apiModule.searchListings === 'function') {
      const geoJson = await apiModule.searchListings(params);
      if (geoJson && Array.isArray(geoJson.features)) {
        const items: ProduceListing[] = [];
        for (const f of geoJson.features) {
          const p = f.properties;
          const coords = f.geometry?.coordinates ?? [params.lng, params.lat];

          const photos: ProducePhoto[] = [];
          if (p.mediaIds && p.mediaIds.length > 0 && typeof apiModule.getMedia === 'function') {
            try {
              const media = await apiModule.getMedia(p.mediaIds[0]);
              photos.push({
                id: media.id,
                url: media.url || media.thumbUrl || '',
                thumbUrl: media.thumbUrl || media.url || '',
                isGeoVerified: media.isGeoVerified,
                geoStatus: media.geoStatus,
                distanceM: media.distanceM,
                rejectReason: media.rejectReason,
              });
            } catch {
              // Ignore single media failure
            }
          }

          items.push({
            id: p.id,
            farmId: p.farmId,
            farmName: p.farmName,
            farmerId: p.farmerId,
            cropId: p.cropId,
            cropName: p.cropName,
            variety: p.variety,
            unit: p.unit,
            pricePerUnit: p.pricePerUnit,
            qtyAvailable: p.qtyAvailable,
            harvestDate: p.harvestDate,
            grade: p.grade,
            isOrganic: p.isOrganic,
            description: p.description,
            distanceM: p.distanceM,
            district: p.district,
            village: p.village,
            status: p.status,
            pickupCoordinates: [coords[0], coords[1]],
            mediaIds: p.mediaIds || [],
            photos,
          });
        }
        return items;
      }
    }
  } catch {
    // Graceful fallback to seed dataset
  }

  // Fallback: Return seed listings with distance dynamically recomputed from user lat/lng
  return SEED_PRODUCE_LISTINGS.map((listing) => {
    const [pLng, pLat] = listing.pickupCoordinates;
    const distanceM = haversineDistanceM(params.lat, params.lng, pLat, pLng);
    return {
      ...listing,
      distanceM,
    };
  });
}

/**
 * Fetch a single listing by ID from the catalog service (GET /catalog/listings/{id}).
 */
export async function fetchListingDetail(listingId: string): Promise<ProduceListing> {
  try {
    const apiModule: CatalogModule | null = await import('../../../../packages/api/src/catalog.ts').catch(() => null);
    if (apiModule && typeof apiModule.getListing === 'function') {
      const out = await apiModule.getListing(listingId);
      const photos: ProducePhoto[] = [];
      if (out.mediaIds && out.mediaIds.length > 0 && typeof apiModule.getMedia === 'function') {
        for (const mId of out.mediaIds) {
          try {
            const media = await apiModule.getMedia(mId);
            photos.push({
              id: media.id,
              url: media.url || media.thumbUrl || '',
              thumbUrl: media.thumbUrl || media.url || '',
              isGeoVerified: media.isGeoVerified,
              geoStatus: media.geoStatus,
              distanceM: media.distanceM,
              rejectReason: media.rejectReason,
            });
          } catch {
            // Ignore single photo failure
          }
        }
      }

      return {
        id: out.id,
        farmId: out.farmId,
        farmName: out.farmName,
        farmerId: out.farmerId,
        cropId: out.cropId,
        cropName: out.cropName,
        variety: out.variety,
        unit: out.unit,
        pricePerUnit: out.pricePerUnit,
        qtyAvailable: out.qtyAvailable,
        harvestDate: out.harvestDate,
        grade: out.grade,
        isOrganic: out.isOrganic,
        description: out.description,
        distanceM: 0,
        district: out.district,
        village: out.village,
        status: out.status,
        pickupCoordinates: out.pickupLocation?.coordinates ?? [0, 0],
        mediaIds: out.mediaIds || [],
        photos,
      };
    }
  } catch {
    // Fallback to seed listing match
  }

  const found = SEED_PRODUCE_LISTINGS.find((s) => s.id === listingId);
  if (found) return found;
  throw new Error(`Listing ${listingId} not found`);
}
