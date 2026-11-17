import type { Shortage } from './cart.ts';

/** What went wrong at checkout, in terms the screens can act on. */
export type CheckoutError =
  | { kind: 'stock'; message: string; shortages: Shortage[] }
  | { kind: 'listing-unavailable'; message: string }
  | { kind: 'payment-failed'; message: string }
  | { kind: 'in-progress'; message: string }
  | { kind: 'attempt-invalid'; message: string }
  | { kind: 'unauthorized'; message: string }
  | { kind: 'validation'; message: string; fields: { field: string; message: string }[] }
  | { kind: 'network'; message: string }
  | { kind: 'server'; message: string; code?: string };

export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: CheckoutError };

export const networkError: CheckoutError = {
  kind: 'network',
  message: 'Could not reach the server. Check your connection and try again.',
};

type ErrorBody = { code?: string; message?: string; details?: unknown };

function errorBody(body: unknown): ErrorBody {
  const error = (body as { error?: unknown } | null | undefined)?.error;
  return typeof error === 'object' && error !== null ? (error as ErrorBody) : {};
}

function toShortages(details: unknown): Shortage[] {
  if (!Array.isArray(details)) return [];
  return details.flatMap((d) => {
    const item = d as Partial<Shortage>;
    return typeof item.listingId === 'string' &&
      typeof item.requested === 'number' &&
      typeof item.available === 'number'
      ? [{ listingId: item.listingId, requested: item.requested, available: item.available }]
      : [];
  });
}

/**
 * Turn an HTTP status and error body of POST /orders (or the payment intent call) into a
 * CheckoutError. Codes are the ones documented in contracts/openapi/order.json and the catalog.
 */
export function parseCheckoutError(status: number | undefined, body: unknown): CheckoutError {
  const { code, message, details } = errorBody(body);
  if (status === 401) {
    return { kind: 'unauthorized', message: 'Please log in to place an order.' };
  }
  if (status === 402 || code === 'PAYMENT_FAILED') {
    return {
      kind: 'payment-failed',
      message: 'Your payment was declined. No money was taken, please try again.',
    };
  }
  if (code === 'STOCK_UNAVAILABLE') {
    return {
      kind: 'stock',
      message: 'Some items are no longer available in the quantity you chose.',
      shortages: toShortages(details),
    };
  }
  if (code === 'LISTING_NOT_AVAILABLE' || code === 'LISTING_NOT_FOUND') {
    return {
      kind: 'listing-unavailable',
      message: 'An item in your cart is no longer for sale. Remove it and try again.',
    };
  }
  if (code === 'IDEMPOTENCY_IN_PROGRESS') {
    return { kind: 'in-progress', message: 'Your order is still being processed. Try again.' };
  }
  if (
    code === 'IDEMPOTENCY_KEY_REUSED' ||
    code === 'PAYMENT_INTENT_USED' ||
    code === 'PAYMENT_INTENT_NOT_FOUND'
  ) {
    return {
      kind: 'attempt-invalid',
      message: 'That checkout attempt cannot be reused. Please try again.',
    };
  }
  if (status === 422 || code === 'VALIDATION_ERROR') {
    const fields = Array.isArray(details)
      ? details.flatMap((d) => {
          const item = d as { field?: string; message?: string };
          return item.field && item.message ? [{ field: item.field, message: item.message }] : [];
        })
      : [];
    return { kind: 'validation', message: 'Please check the details you entered.', fields };
  }
  if (status === 503 || code === 'CATALOG_UNAVAILABLE') {
    return { kind: 'network', message: 'The shop is busy right now. Please try again shortly.' };
  }
  return { kind: 'server', message: message ?? 'Something went wrong. Please try again.', code };
}
