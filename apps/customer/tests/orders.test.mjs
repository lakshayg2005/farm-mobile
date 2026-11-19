import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  STATUS_META, TIMELINE_STEPS, addressLabel, itemsLabel, notificationOrderId, orderTitle,
  splitOrders, stepIndex, stepTimes, timeAgo,
} from '../src/orders/orders.ts';

const ALL_STATUSES = [
  'PLACED', 'CONFIRMED', 'PACKED', 'QC_PASSED', 'READY_FOR_DISPATCH', 'POOLED', 'IN_TRANSIT',
  'DELIVERED', 'COMPLETED', 'CANCELLED',
];

test('every contract status has a label and a timeline step', () => {
  for (const s of ALL_STATUSES) {
    assert.ok(STATUS_META[s]?.label, s);
    assert.ok(stepIndex(s) < TIMELINE_STEPS.length, s);
  }
  assert.equal(stepIndex('QC_PASSED'), stepIndex('PACKED'));
  assert.equal(stepIndex('POOLED'), stepIndex('READY_FOR_DISPATCH'));
  assert.equal(stepIndex('COMPLETED'), stepIndex('DELIVERED'));
  assert.equal(stepIndex('CANCELLED'), -1);
});

test('active orders come before finished ones, each newest first', () => {
  const o = (id, status, createdAt) => ({ id, status, createdAt });
  const { active, past } = splitOrders([
    o('a', 'DELIVERED', '2026-11-10T00:00:00Z'),
    o('b', 'PLACED', '2026-11-12T00:00:00Z'),
    o('c', 'IN_TRANSIT', '2026-11-15T00:00:00Z'),
    o('d', 'CANCELLED', '2026-11-14T00:00:00Z'),
    o('e', 'COMPLETED', '2026-11-11T00:00:00Z'),
  ]);
  assert.deepEqual(active.map((x) => x.id), ['c', 'b']);
  assert.deepEqual(past.map((x) => x.id), ['d', 'e', 'a']);
});

test('titles and labels', () => {
  assert.equal(orderTitle({ orderType: 'PRODUCE' }), 'Produce order');
  assert.equal(orderTitle({ orderType: 'HARVEST', harvestOption: 'BUYBACK' }), 'Harvest buy-back');
  assert.equal(orderTitle({ orderType: 'HARVEST', harvestOption: 'DELIVERY' }), 'Harvest delivery');
  assert.equal(itemsLabel(1), '1 item');
  assert.equal(itemsLabel(3), '3 items');
  assert.equal(addressLabel(null), null);
  assert.equal(
    addressLabel({ line1: 'Flat 4', line2: null, city: 'Raipur', pincode: '492001', location: { lat: 0, lng: 0 } }),
    'Flat 4, Raipur, 492001',
  );
});

test('each timeline step is timed by the first event that reaches it', () => {
  const times = stepTimes([
    { status: 'PACKED', occurredAt: '2026-11-12T10:00:00Z' },
    { status: 'PLACED', occurredAt: '2026-11-12T08:00:00Z' },
    { status: 'CONFIRMED', occurredAt: '2026-11-12T08:01:00Z' },
    { status: 'QC_PASSED', occurredAt: '2026-11-12T11:00:00Z' },
  ]);
  assert.deepEqual(times, [
    '2026-11-12T08:00:00Z', '2026-11-12T08:01:00Z', '2026-11-12T10:00:00Z', null, null, null,
  ]);
});

test('order notifications link to their order, others do not', () => {
  assert.equal(notificationOrderId({ orderId: 'o-1', total: 128 }), 'o-1');
  assert.equal(notificationOrderId({ paymentId: 'p-1' }), null);
  assert.equal(notificationOrderId({ orderId: '' }), null);
  assert.equal(notificationOrderId(undefined), null);
});

test('timeAgo', () => {
  const now = new Date('2026-11-19T12:00:00Z');
  assert.equal(timeAgo('2026-11-19T11:59:40Z', now), 'just now');
  assert.equal(timeAgo('2026-11-19T11:15:00Z', now), '45m ago');
  assert.equal(timeAgo('2026-11-19T07:00:00Z', now), '5h ago');
  assert.equal(timeAgo('2026-11-16T12:00:00Z', now), '3d ago');
});
