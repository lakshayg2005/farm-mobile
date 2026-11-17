import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseCheckoutError } from '../src/checkout/errors.ts';

const body = (code, details) => ({ error: { code, message: code, details } });

test('stock unavailable keeps the per item details', () => {
  const details = [{ listingId: 'l1', requested: 5, available: 3 }, { nope: true }];

  const error = parseCheckoutError(409, body('STOCK_UNAVAILABLE', details));

  assert.equal(error.kind, 'stock');
  assert.deepEqual(error.shortages, [{ listingId: 'l1', requested: 5, available: 3 }]);
});

test('a declined payment is told apart by status or code', () => {
  assert.equal(parseCheckoutError(402, body('PAYMENT_FAILED')).kind, 'payment-failed');
  assert.equal(parseCheckoutError(undefined, body('PAYMENT_FAILED')).kind, 'payment-failed');
});

test('items that are no longer sold', () => {
  assert.equal(parseCheckoutError(409, body('LISTING_NOT_AVAILABLE')).kind, 'listing-unavailable');
  assert.equal(parseCheckoutError(404, body('LISTING_NOT_FOUND')).kind, 'listing-unavailable');
});

test('idempotency and payment intent problems mean the attempt must be restarted', () => {
  for (const code of [
    'IDEMPOTENCY_KEY_REUSED',
    'PAYMENT_INTENT_USED',
    'PAYMENT_INTENT_NOT_FOUND',
  ]) {
    assert.equal(parseCheckoutError(409, body(code)).kind, 'attempt-invalid', code);
  }
  assert.equal(parseCheckoutError(409, body('IDEMPOTENCY_IN_PROGRESS')).kind, 'in-progress');
});

test('401 asks the customer to log in', () => {
  assert.equal(parseCheckoutError(401, body('UNAUTHORIZED')).kind, 'unauthorized');
});

test('validation errors list the fields', () => {
  const error = parseCheckoutError(
    422,
    body('VALIDATION_ERROR', [{ field: 'body.deliveryAddress.pincode', message: 'bad' }]),
  );

  assert.equal(error.kind, 'validation');
  assert.deepEqual(error.fields, [{ field: 'body.deliveryAddress.pincode', message: 'bad' }]);
});

test('anything else is a server error with the code kept', () => {
  const error = parseCheckoutError(500, { error: { code: 'BOOM', message: 'Broken' } });

  assert.deepEqual(error, { kind: 'server', message: 'Broken', code: 'BOOM' });
  assert.equal(parseCheckoutError(500, null).kind, 'server');
});

test('catalog being down is shown as a retryable problem', () => {
  assert.equal(parseCheckoutError(503, body('CATALOG_UNAVAILABLE')).kind, 'network');
});
