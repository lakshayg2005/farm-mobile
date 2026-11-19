import assert from 'node:assert/strict';
import { test } from 'node:test';
import { localISODate, markStopDone, routeStops, routeSummary, toRouteStop } from '../src/route/route.ts';

const stop = (over) => ({
  id: 's1', poolId: 'p1', orderId: 'aaaaaaaa-1111-2222-3333-444444444444', stopType: 'PICKUP', seq: 1,
  lat: 21.25, lng: 81.63, status: 'PENDING', otpVerified: false, ...over,
});

test('a pickup stop maps onto the screen shape', () => {
  assert.deepEqual(toRouteStop(stop({ weightKg: 12.5 })), {
    id: 's1', orderId: 'aaaaaaaa-1111-2222-3333-444444444444', seq: 1, type: 'pickup', status: 'pending',
    label: 'Farm pickup for order #aaaaaaaa', address: '21.25000, 81.63000', lat: 21.25, lng: 81.63,
    customerName: 'Farm', items: ['Order #aaaaaaaa · 12.5 kg'],
  });
});

test('a drop stop carries the customer, address and OTP', () => {
  const r = toRouteStop(stop({
    id: 's2', stopType: 'DROP', seq: 2, status: 'ARRIVED', customerName: 'Asha Verma',
    address: 'Flat 4, Raipur', otp: '4821',
  }));
  assert.equal(r.type, 'drop');
  assert.equal(r.status, 'pending'); // arrived but not delivered yet
  assert.equal(r.label, 'Customer: Asha Verma');
  assert.equal(r.address, 'Flat 4, Raipur');
  assert.equal(r.otp, '4821');
  assert.equal(toRouteStop(stop({ stopType: 'DROP', customerName: '  ' })).label, 'Customer: order #aaaaaaaa');
});

test('stops come in seq order and the summary finds the next one', () => {
  const pool = {
    id: 'p1', poolDate: '2026-12-03', partnerId: 'u1', status: 'IN_PROGRESS', totalKm: 9, kmSaved: 2,
    createdAt: '2026-11-19T00:00:00Z', assignedAt: null,
    stops: [
      stop({ id: 'd1', stopType: 'DROP', seq: 3 }),
      stop({ id: 'p1', seq: 1, status: 'DONE' }),
      stop({ id: 'p2', seq: 2, status: 'SKIPPED' }),
    ],
  };
  const stops = routeStops(pool);
  assert.deepEqual(stops.map((s) => s.id), ['p1', 'p2', 'd1']);
  assert.deepEqual(
    { ...routeSummary(stops), next: routeSummary(stops).next?.id },
    { pickups: 2, drops: 1, done: 1, finished: false, next: 'd1' },
  );
  const after = routeStops(markStopDone(pool, 'd1'));
  assert.equal(routeSummary(after).finished, true);
  assert.equal(routeSummary(after).next, null);
  assert.equal(pool.stops[0].status, 'PENDING'); // the cached pool is not mutated
});

test('no pool means no stops, and an empty route is not "finished"', () => {
  assert.deepEqual(routeStops(null), []);
  assert.equal(routeSummary([]).finished, false);
});

test('localISODate uses the local calendar day', () => {
  assert.equal(localISODate(new Date(2026, 0, 5, 23, 30)), '2026-01-05');
});
