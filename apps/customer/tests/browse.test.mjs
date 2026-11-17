import assert from 'node:assert/strict';
import { test } from 'node:test';
import { filterListings, sortListings } from '../src/browse/filterLogic.ts';
import { formatDistance, formatHarvestDate, formatGradeBadge, getPhotoVerification } from '../src/browse/format.ts';
import { addItem, emptyCart } from '../src/checkout/cart.ts';

const SAMPLE_LISTINGS = [
  {
    id: 'lst-1',
    farmId: 'farm-1',
    farmName: 'Green Acres Farm',
    farmerId: 'farmer-1',
    cropId: 'tomato',
    cropName: 'Tomato',
    variety: 'Himsona Hybrid',
    unit: 'kg',
    pricePerUnit: 35,
    qtyAvailable: 150,
    harvestDate: '2026-11-16',
    grade: 'A',
    isOrganic: true,
    description: 'Juicy organic hybrid tomatoes',
    distanceM: 1200,
    district: 'Durg',
    village: 'Kumhari',
    status: 'ACTIVE',
    pickupCoordinates: [81.29, 21.19],
    mediaIds: ['med-1'],
    photos: [
      {
        id: 'med-1',
        url: 'https://example.com/tomato.jpg',
        thumbUrl: 'https://example.com/tomato_thumb.jpg',
        isGeoVerified: true,
        geoStatus: 'VERIFIED',
        distanceM: 40,
      },
    ],
  },
  {
    id: 'lst-2',
    farmId: 'farm-2',
    farmName: 'Sunrise Fields',
    farmerId: 'farmer-2',
    cropId: 'spinach',
    cropName: 'Spinach',
    variety: 'Desi',
    unit: 'bunch',
    pricePerUnit: 15,
    qtyAvailable: 80,
    harvestDate: '2026-11-17',
    grade: 'A',
    isOrganic: true,
    description: 'Fresh organic spinach',
    distanceM: 4500,
    district: 'Durg',
    village: 'Dhamdha',
    status: 'ACTIVE',
    pickupCoordinates: [81.30, 21.21],
    mediaIds: ['med-2'],
    photos: [
      {
        id: 'med-2',
        url: 'https://example.com/spinach.jpg',
        thumbUrl: 'https://example.com/spinach_thumb.jpg',
        isGeoVerified: true,
        geoStatus: 'VERIFIED',
        distanceM: 75,
      },
    ],
  },
  {
    id: 'lst-3',
    farmId: 'farm-1',
    farmName: 'Green Acres Farm',
    farmerId: 'farmer-1',
    cropId: 'onion',
    cropName: 'Red Onion',
    variety: 'Nasik Red',
    unit: 'kg',
    pricePerUnit: 25,
    qtyAvailable: 300,
    harvestDate: '2026-11-10',
    grade: 'B',
    isOrganic: false,
    description: 'Crisp red onions',
    distanceM: 1200,
    district: 'Durg',
    village: 'Kumhari',
    status: 'ACTIVE',
    pickupCoordinates: [81.29, 21.19],
    mediaIds: ['med-3'],
    photos: [
      {
        id: 'med-3',
        url: 'https://example.com/onion.jpg',
        thumbUrl: 'https://example.com/onion_thumb.jpg',
        isGeoVerified: false,
        geoStatus: 'NO_GPS',
        distanceM: null,
        rejectReason: 'Missing GPS metadata',
      },
    ],
  },
  {
    id: 'lst-4',
    farmId: 'farm-3',
    farmName: 'Raipur Agro',
    farmerId: 'farmer-3',
    cropId: 'potato',
    cropName: 'Potato',
    variety: 'Kufri Jyoti',
    unit: 'kg',
    pricePerUnit: 20,
    qtyAvailable: 500,
    harvestDate: '2026-11-05',
    grade: 'C',
    isOrganic: false,
    description: 'Commercial grade potatoes',
    distanceM: 18000,
    district: 'Raipur',
    village: 'Mandir Hasaud',
    status: 'ACTIVE',
    pickupCoordinates: [81.65, 21.24],
    mediaIds: ['med-4'],
    photos: [
      {
        id: 'med-4',
        url: 'https://example.com/potato.jpg',
        thumbUrl: 'https://example.com/potato_thumb.jpg',
        isGeoVerified: false,
        geoStatus: 'TOO_FAR',
        distanceM: 3500,
      },
    ],
  },
];

const DEFAULT_FILTERS = {
  query: '',
  crop: null,
  radiusKm: 50,
  organicOnly: false,
  minGrade: 'ALL',
  minPrice: null,
  maxPrice: null,
  sort: 'distance',
};

test('filters produce by search text (query)', () => {
  const resultTomato = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, query: 'tomato' });
  assert.equal(resultTomato.length, 1);
  assert.equal(resultTomato[0].id, 'lst-1');

  const resultFarm = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, query: 'Sunrise' });
  assert.equal(resultFarm.length, 1);
  assert.equal(resultFarm[0].id, 'lst-2');

  const resultVariety = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, query: 'Nasik' });
  assert.equal(resultVariety.length, 1);
  assert.equal(resultVariety[0].id, 'lst-3');
});

test('filters produce by crop id', () => {
  const result = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, crop: 'onion' });
  assert.equal(result.length, 1);
  assert.equal(result[0].cropId, 'onion');
});

test('filters produce by distance radius in km', () => {
  // Radius of 5 km should only include lst-1 (1.2km), lst-2 (4.5km), lst-3 (1.2km), excluding lst-4 (18km)
  const result5km = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, radiusKm: 5 });
  assert.equal(result5km.length, 3);
  assert.ok(!result5km.some((l) => l.id === 'lst-4'));

  // Radius of 2 km should only include lst-1 and lst-3
  const result2km = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, radiusKm: 2 });
  assert.equal(result2km.length, 2);
  assert.deepEqual(result2km.map((l) => l.id).sort(), ['lst-1', 'lst-3']);
});

test('filters produce by organic only flag', () => {
  const result = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, organicOnly: true });
  assert.equal(result.length, 2);
  assert.ok(result.every((l) => l.isOrganic));
});

test('filters produce by minimum quality grade', () => {
  // Grade A only
  const gradeA = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, minGrade: 'A' });
  assert.equal(gradeA.length, 2);
  assert.ok(gradeA.every((l) => l.grade === 'A'));

  // Grade B and above (A and B)
  const gradeB = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, minGrade: 'B' });
  assert.equal(gradeB.length, 3);
  assert.ok(gradeB.every((l) => l.grade === 'A' || l.grade === 'B'));

  // Grade C and above (all 4)
  const gradeC = filterListings(SAMPLE_LISTINGS, { ...DEFAULT_FILTERS, minGrade: 'C' });
  assert.equal(gradeC.length, 4);
});

test('filters produce by price bounds', () => {
  const priceFilter = filterListings(SAMPLE_LISTINGS, {
    ...DEFAULT_FILTERS,
    minPrice: 20,
    maxPrice: 30,
  });
  // Should include lst-3 (25) and lst-4 (20)
  assert.equal(priceFilter.length, 2);
  assert.deepEqual(priceFilter.map((l) => l.id).sort(), ['lst-3', 'lst-4']);
});

test('sorts produce listings by distance (nearest first)', () => {
  const sorted = sortListings(SAMPLE_LISTINGS, 'distance');
  assert.equal(sorted[0].distanceM, 1200);
  assert.equal(sorted[sorted.length - 1].distanceM, 18000);
});

test('sorts produce listings by price ascending and descending', () => {
  const asc = sortListings(SAMPLE_LISTINGS, 'price_asc');
  assert.equal(asc[0].pricePerUnit, 15);
  assert.equal(asc[asc.length - 1].pricePerUnit, 35);

  const desc = sortListings(SAMPLE_LISTINGS, 'price_desc');
  assert.equal(desc[0].pricePerUnit, 35);
  assert.equal(desc[desc.length - 1].pricePerUnit, 15);
});

test('sorts produce listings by harvest freshness (newest first)', () => {
  const freshest = sortListings(SAMPLE_LISTINGS, 'harvest_date');
  assert.equal(freshest[0].id, 'lst-2'); // 2026-11-17
  assert.equal(freshest[freshest.length - 1].id, 'lst-4'); // 2026-11-05
});

test('formats distance correctly in metres and kilometres', () => {
  assert.equal(formatDistance(350), '350 m');
  assert.equal(formatDistance(999), '999 m');
  assert.equal(formatDistance(1200), '1.2 km');
  assert.equal(formatDistance(4500), '4.5 km');
  assert.equal(formatDistance(18000), '18 km');
});

test('formats harvest dates into readable text', () => {
  assert.equal(formatHarvestDate('2026-11-16'), '16 Nov 2026');
  assert.equal(formatHarvestDate('2026-11-05'), '5 Nov 2026');
});

test('formats grade badge metadata', () => {
  const gradeA = formatGradeBadge('A');
  assert.equal(gradeA.label, 'Grade A');
  assert.ok(gradeA.description.includes('Premium quality'));

  const ungraded = formatGradeBadge(null);
  assert.equal(ungraded.label, 'Ungraded');
});

test('photo verification badge identifies GPS-verified photos and flags unverified photos', () => {
  // Verified photo
  const verified = getPhotoVerification(SAMPLE_LISTINGS[0].photos[0]);
  assert.equal(verified.isVerified, true);
  assert.equal(verified.shortLabel, '✓ Verified');
  assert.ok(verified.description.includes('GPS verified'));

  // Unverified photo without GPS
  const noGps = getPhotoVerification(SAMPLE_LISTINGS[2].photos[0]);
  assert.equal(noGps.isVerified, false);
  assert.equal(noGps.shortLabel, '⚠ Unverified');
  assert.ok(noGps.description.includes('GPS'));

  // Unverified photo taken too far from farm
  const tooFar = getPhotoVerification(SAMPLE_LISTINGS[3].photos[0]);
  assert.equal(tooFar.isVerified, false);
  assert.equal(tooFar.shortLabel, '⚠ Unverified');
  assert.ok(tooFar.description.includes('away') || tooFar.description.includes('far'));

  // Null photo fallback
  const nullPhoto = getPhotoVerification(null);
  assert.equal(nullPhoto.isVerified, false);
  assert.equal(nullPhoto.shortLabel, 'Unverified');
});

test('adding produce item from browse directly updates the cart with required fields', () => {
  const produce = SAMPLE_LISTINGS[0];
  const cart = addItem(emptyCart, {
    listingId: produce.id,
    farmId: produce.farmId,
    farmName: produce.farmName,
    name: `${produce.cropName} (${produce.variety})`,
    unit: produce.unit,
    unitPrice: produce.pricePerUnit,
    qty: 2,
    maxQty: produce.qtyAvailable,
  });

  assert.equal(cart.items.length, 1);
  assert.equal(cart.items[0].listingId, 'lst-1');
  assert.equal(cart.items[0].qty, 2);
  assert.equal(cart.items[0].unitPrice, 35);
  assert.equal(cart.items[0].farmName, 'Green Acres Farm');
  assert.equal(cart.items[0].maxQty, 150);
});
