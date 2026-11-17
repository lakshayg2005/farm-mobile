import assert from 'node:assert/strict';
import { test } from 'node:test';
import { addItem, emptyCart } from '../src/checkout/cart.ts';
import { CheckoutSession } from '../src/checkout/checkoutFlow.ts';
import { networkError } from '../src/checkout/errors.ts';
import { newIdempotencyKey } from '../src/checkout/idempotency.ts';

const tomato = {
  listingId: 'l-tomato',
  farmId: 'farm-a',
  farmName: 'Green Farm',
  name: 'Tomato',
  unit: 'kg',
  unitPrice: 40,
};
const cart = addItem(emptyCart, { ...tomato, qty: 2 });
const address = {
  line1: '12 Green Park',
  city: 'Jaipur',
  pincode: '302001',
  location: { type: 'Point', coordinates: [75.8, 26.9] },
};
const checkout = { id: 'c1', orders: [], total: 100 };

/** A fake order API that plays back queued results. */
function fakeApi(placeResults) {
  const calls = { intents: [], placed: [] };
  let intentNumber = 0;
  return {
    calls,
    async createPaymentIntent(amount) {
      calls.intents.push(amount);
      return { ok: true, data: { id: `intent-${++intentNumber}` } };
    },
    async placeOrder(args) {
      calls.placed.push(args);
      return placeResults.shift() ?? { ok: true, data: checkout };
    },
  };
}

function session(api) {
  let n = 0;
  return new CheckoutSession(api, 20, () => `key-${++n}`);
}

test('first submit creates a payment intent for the estimated total and places the order', async () => {
  const api = fakeApi([]);

  const result = await session(api).submit(cart, address, 'Ring the bell');

  assert.equal(result.ok, true);
  assert.deepEqual(api.calls.intents, [100]); // 2 x 40 + 20 delivery
  assert.deepEqual(api.calls.placed, [
    {
      key: 'key-1',
      body: {
        items: [{ listingId: 'l-tomato', qty: 2 }],
        deliveryAddress: address,
        paymentIntentId: 'intent-1',
        notes: 'Ring the bell',
      },
    },
  ]);
});

test('a retry after a network error reuses the key and the payment intent', async () => {
  const api = fakeApi([{ ok: false, error: networkError }]);
  const flow = session(api);

  const first = await flow.submit(cart, address);
  const second = await flow.submit(cart, address);

  assert.equal(first.ok, false);
  assert.equal(second.ok, true);
  assert.equal(api.calls.intents.length, 1);
  assert.deepEqual(
    api.calls.placed.map((p) => p.key),
    ['key-1', 'key-1'],
  );
  assert.deepEqual(
    api.calls.placed.map((p) => p.body.paymentIntentId),
    ['intent-1', 'intent-1'],
  );
});

test('a declined payment ends the attempt: the next try gets a new key and intent', async () => {
  const api = fakeApi([{ ok: false, error: { kind: 'payment-failed', message: 'declined' } }]);
  const flow = session(api);

  await flow.submit(cart, address);
  await flow.submit(cart, address);

  assert.deepEqual(
    api.calls.placed.map((p) => p.key),
    ['key-1', 'key-2'],
  );
  assert.deepEqual(
    api.calls.placed.map((p) => p.body.paymentIntentId),
    ['intent-1', 'intent-2'],
  );
});

test('changing the cart (for example after a stock error) starts a new attempt', async () => {
  const stock = { kind: 'stock', message: 'short', shortages: [] };
  const api = fakeApi([{ ok: false, error: stock }]);
  const flow = session(api);

  await flow.submit(cart, address);
  await flow.submit(addItem(cart, { ...tomato, qty: 1 }), address);

  assert.deepEqual(
    api.calls.placed.map((p) => p.key),
    ['key-1', 'key-2'],
  );
});

test('changing the address starts a new attempt', async () => {
  const api = fakeApi([{ ok: false, error: networkError }]);
  const flow = session(api);

  await flow.submit(cart, address);
  await flow.submit(cart, { ...address, city: 'Udaipur' });

  assert.deepEqual(
    api.calls.placed.map((p) => p.key),
    ['key-1', 'key-2'],
  );
});

test('after success the next checkout starts fresh', async () => {
  const api = fakeApi([]);
  const flow = session(api);

  await flow.submit(cart, address);
  assert.equal(flow.currentKey, null);
  await flow.submit(cart, address);

  assert.deepEqual(
    api.calls.placed.map((p) => p.key),
    ['key-1', 'key-2'],
  );
});

test('a key the server already used with another request starts over next time', async () => {
  const api = fakeApi([{ ok: false, error: { kind: 'attempt-invalid', message: 'x' } }]);
  const flow = session(api);

  await flow.submit(cart, address);

  assert.equal(flow.currentKey, null);
});

test('a failing payment intent call stops before placing an order', async () => {
  const api = fakeApi([]);
  api.createPaymentIntent = async () => ({ ok: false, error: networkError });

  const result = await session(api).submit(cart, address);

  assert.equal(result.ok, false);
  assert.equal(api.calls.placed.length, 0);
});

test('idempotency keys are unique UUID-shaped strings the server accepts (8 to 128 chars)', () => {
  const keys = new Set(Array.from({ length: 50 }, newIdempotencyKey));

  assert.equal(keys.size, 50);
  for (const key of keys)
    assert.match(key, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
