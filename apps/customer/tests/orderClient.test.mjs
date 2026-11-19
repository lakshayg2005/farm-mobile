import { test } from 'node:test';
import assert from 'node:assert/strict';

// The gateway serves the order service under /api/v1/order/..., and the order contract's paths are
// service-relative (/orders), so the order client must add the /order prefix itself.
// (packages/api/src/order.ts is `createServiceClient('order')`; Node can't load it directly because of
// its extensionless imports, so this checks the helper it uses.)
test('a service client calls through the /<service> gateway prefix with the bearer token', async () => {
  const { BASE_URL, createServiceClient, setAuthToken } = await import('../../../packages/api/src/client.ts');
  const seen = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (input) => {
    seen.push({ url: input.url, auth: input.headers.get('Authorization') });
    return new Response(JSON.stringify({ items: [], nextCursor: null }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  };
  try {
    const orderClient = createServiceClient('order'); // after the stub: openapi-fetch keeps the fetch it saw
    setAuthToken('tok-1');
    await orderClient.GET('/orders', {});
    setAuthToken('tok-2');
    await orderClient.POST('/orders/payments/intent', { body: { amount: 10, kind: 'FULL' } });
  } finally {
    globalThis.fetch = realFetch;
    setAuthToken(null);
  }
  assert.deepEqual(seen, [
    { url: `${BASE_URL}/order/orders`, auth: 'Bearer tok-1' },
    { url: `${BASE_URL}/order/orders/payments/intent`, auth: 'Bearer tok-2' },
  ]);
});
