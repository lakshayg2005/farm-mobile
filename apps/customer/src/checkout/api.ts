import { orderClient, type OrderClient } from '@farm/api/src/order';
import type { CheckoutApi } from './checkoutFlow.ts';
import { networkError, parseCheckoutError, type ApiResult } from './errors.ts';

/** The real calls to the order service through the gateway. */
export function createCheckoutApi(client: OrderClient = orderClient): CheckoutApi {
  return {
    async createPaymentIntent(amount): Promise<ApiResult<{ id: string }>> {
      try {
        const { data, error, response } = await client.POST('/orders/payments/intent', {
          body: { amount, kind: 'FULL' },
        });
        if (data) return { ok: true, data: { id: data.id } };
        return { ok: false, error: parseCheckoutError(response.status, error) };
      } catch {
        return { ok: false, error: networkError };
      }
    },

    async placeOrder({ key, body }) {
      try {
        const { data, error, response } = await client.POST('/orders', {
          params: { header: { 'Idempotency-Key': key } },
          body,
        });
        if (data) return { ok: true, data };
        return { ok: false, error: parseCheckoutError(response.status, error) };
      } catch {
        return { ok: false, error: networkError };
      }
    },
  };
}
