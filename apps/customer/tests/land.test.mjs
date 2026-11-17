import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  filterPlots,
  sortPlots,
  calculateReservationEstimate,
  getStageMetadata,
} from '../src/land/filterLogic.ts';
import {
  fetchPlots,
  submitPlotReservation,
  fetchCustomerReservations,
} from '../src/land/api.ts';
import { SEED_PLOTS } from '../src/land/mockData.ts';

const DEFAULT_LAND_FILTERS = {
  query: '',
  season: null,
  crop: null,
  soilType: null,
  radiusKm: 50,
  minAcres: null,
  maxAcres: null,
  sort: 'distance',
};

test('filters plots by search query on title, village, and description', () => {
  const resultTitle = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, query: 'Kumhari' });
  assert.equal(resultTitle.length, 1);
  assert.equal(resultTitle[0].id, 'plot-durg-01');

  const resultDesc = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, query: 'solar drip' });
  assert.equal(resultDesc.length, 1);
  assert.equal(resultDesc[0].id, 'plot-durg-02');
});

test('filters plots by season', () => {
  const kharifPlots = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, season: 'Kharif 2026' });
  assert.equal(kharifPlots.length, 1);
  assert.equal(kharifPlots[0].id, 'plot-durg-01');

  const rabiPlots = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, season: 'Rabi 2026-27' });
  assert.equal(rabiPlots.length, 4);
});

test('filters plots by crop capability across seasons', () => {
  const tomatoPlots = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, crop: 'tomato' });
  // Plots supporting tomato in any season: plot-durg-01 and plot-durg-02
  assert.equal(tomatoPlots.length, 2);
  assert.deepEqual(tomatoPlots.map((p) => p.id).sort(), ['plot-durg-01', 'plot-durg-02']);

  const onionPlots = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, crop: 'onion' });
  assert.equal(onionPlots.length, 2);
});

test('filters plots by soil type', () => {
  const blackCotton = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, soilType: 'Black Cotton' });
  assert.equal(blackCotton.length, 1);
  assert.equal(blackCotton[0].soilType, 'Black Cotton');

  const clayLoam = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, soilType: 'Clay Loam' });
  assert.equal(clayLoam.length, 1);
  assert.equal(clayLoam[0].id, 'plot-durg-01');
});

test('filters plots by radius distance', () => {
  const within4km = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, radiusKm: 4 });
  // Kumhari (1.8km) and Dhamdha (3.4km)
  assert.equal(within4km.length, 2);
  assert.ok(!within4km.some((p) => p.id === 'plot-raipur-04'));
});

test('filters plots by minimum and maximum acreage', () => {
  const largePlots = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, minAcres: 3.0 });
  // plot-durg-02 (3.8) and plot-raipur-04 (4.2)
  assert.equal(largePlots.length, 2);
  assert.ok(largePlots.every((p) => p.areaAcres >= 3.0));

  const smallPlots = filterPlots(SEED_PLOTS, { ...DEFAULT_LAND_FILTERS, maxAcres: 2.0 });
  // plot-durg-03 (1.8)
  assert.equal(smallPlots.length, 1);
  assert.equal(smallPlots[0].id, 'plot-durg-03');
});

test('sorts plots by distance and area', () => {
  const byDistance = sortPlots(SEED_PLOTS, 'distance');
  assert.equal(byDistance[0].id, 'plot-durg-01'); // 1800m
  assert.equal(byDistance[byDistance.length - 1].id, 'plot-raipur-04'); // 9200m

  const byAreaDesc = sortPlots(SEED_PLOTS, 'acres_desc');
  assert.equal(byAreaDesc[0].areaAcres, 4.2);
  assert.equal(byAreaDesc[byAreaDesc.length - 1].areaAcres, 1.8);
});

test('calculates reservation financial estimates and 20% deposit', () => {
  // 100 kg at Rs 28/kg -> Total = 2800, Deposit = 560
  const est1 = calculateReservationEstimate(28, 100);
  assert.equal(est1.total, 2800);
  assert.equal(est1.deposit, 560);

  // 250 kg at Rs 35/kg -> Total = 8750, Deposit = 1750
  const est2 = calculateReservationEstimate(35, 250);
  assert.equal(est2.total, 8750);
  assert.equal(est2.deposit, 1750);
});

test('returns correct stage metadata and step ranking for crop growth lifecycle', () => {
  const sown = getStageMetadata('SOWN');
  assert.equal(sown.stepIndex, 1);
  assert.equal(sown.icon, '🌱');

  const growing = getStageMetadata('GROWING');
  assert.equal(growing.stepIndex, 2);
  assert.equal(growing.icon, '🌿');

  const flowering = getStageMetadata('FLOWERING');
  assert.equal(flowering.stepIndex, 3);
  assert.equal(flowering.icon, '🌸');

  const harvest = getStageMetadata('HARVEST_READY');
  assert.equal(harvest.stepIndex, 4);
  assert.equal(harvest.icon, '🌾');
});

test('submitting a plot reservation records agreement and initializes timeline', async () => {
  const targetPlot = SEED_PLOTS[0];
  const targetSeason = targetPlot.seasons[0];

  const initialCount = (await fetchCustomerReservations()).length;

  const newRes = await submitPlotReservation({
    plot: targetPlot,
    season: targetSeason,
    cropId: 'tomato',
    expectedQtyKg: 150,
  });

  assert.ok(newRes.id);
  assert.equal(newRes.cropId, 'tomato');
  assert.equal(newRes.expectedQtyKg, 150);
  assert.equal(newRes.status, 'PENDING');
  assert.equal(newRes.total, 4200); // 150 * 28
  assert.equal(newRes.deposit, 840); // 20% of 4200
  assert.equal(newRes.updates.length, 1);
  assert.equal(newRes.updates[0].stage, 'SOWN');

  const updatedReservations = await fetchCustomerReservations();
  assert.equal(updatedReservations.length, initialCount + 1);
});
