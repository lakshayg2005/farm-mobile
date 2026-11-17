import { estimateTotal, toOrderItems, type Cart } from './cart.ts';
import type { ApiResult } from './errors.ts';
import { newIdempotencyKey } from './idempotency.ts';
import type { Checkout, CheckoutRequest, DeliveryAddress } from '@farm/api/src/order';

/** The two calls checkout makes. The real one is in api.ts; tests pass a fake. */
export interface CheckoutApi {
  createPaymentIntent(amount: number): Promise<ApiResult<{ id: string }>>;
  placeOrder(args: { key: string; body: CheckoutRequest }): Promise<ApiResult<Checkout>>;
}

type Attempt = { key: string; intentId: string | null; fingerprint: string };

/** Same cart and address give the same fingerprint; changing either starts a new attempt. */
export function fingerprint(cart: Cart, address: DeliveryAddress, notes?: string): string {
  const items = [...toOrderItems(cart)].sort((a, b) => a.listingId.localeCompare(b.listingId));
  return JSON.stringify({ items, address, notes: notes ?? null });
}

/**
 * Runs the checkout and keeps the Idempotency-Key and payment intent between taps, so that a
 * retry after a network error can never create a second order:
 *  - same cart + same address     -> same key and intent (the server returns the first result)
 *  - cart or address changed       -> new key and a new payment intent
 *  - payment declined / success    -> the attempt is over; the next one starts fresh
 */
export class CheckoutSession {
  private attempt: Attempt | null = null;
  private readonly api: CheckoutApi;
  private readonly feePerFarm: number;
  private readonly newKey: () => string;

  constructor(api: CheckoutApi, feePerFarm: number, newKey: () => string = newIdempotencyKey) {
    this.api = api;
    this.feePerFarm = feePerFarm;
    this.newKey = newKey;
  }

  async submit(cart: Cart, address: DeliveryAddress, notes?: string): Promise<ApiResult<Checkout>> {
    const print = fingerprint(cart, address, notes);
    if (!this.attempt || this.attempt.fingerprint !== print) {
      this.attempt = { key: this.newKey(), intentId: null, fingerprint: print };
    }
    const attempt = this.attempt;

    if (!attempt.intentId) {
      const intent = await this.api.createPaymentIntent(estimateTotal(cart, this.feePerFarm));
      if (!intent.ok) return intent;
      attempt.intentId = intent.data.id;
    }

    const result = await this.api.placeOrder({
      key: attempt.key,
      body: {
        items: toOrderItems(cart),
        deliveryAddress: address,
        paymentIntentId: attempt.intentId,
        ...(notes ? { notes } : {}),
      },
    });

    if (result.ok) {
      this.attempt = null;
    } else if (result.error.kind === 'payment-failed' || result.error.kind === 'attempt-invalid') {
      this.attempt = null; // the server remembers this key; a retry needs a new one
    }
    return result;
  }

  /** The key of the attempt in progress, for tests and logs. */
  get currentKey(): string | null {
    return this.attempt?.key ?? null;
  }
}
