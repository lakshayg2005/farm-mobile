import type createClient from 'openapi-fetch';
import { client } from './client';
import type { components, paths } from './generated/order';

/**
 * The shared client (base URL and auth token from ./client) typed with the order service contract
 * (contracts/openapi/order.json). Regenerate the types with:
 *   pnpm --filter @farm/api exec openapi-typescript <path to order.json> -o src/generated/order.d.ts
 * TODO(M1): once `pnpm gen:api` produces one combined schema, use `client` directly.
 */
export type OrderClient = ReturnType<typeof createClient<paths>>;
export const orderClient = client as unknown as OrderClient;

type Schemas = components['schemas'];
export type Checkout = Schemas['Checkout'];
export type CheckoutRequest = Schemas['CheckoutRequest'];
export type DeliveryAddress = Schemas['DeliveryAddress'];
export type Order = Schemas['Order'];
export type OrderStatus = Schemas['OrderStatus'];
export type PaymentIntent = Schemas['PaymentIntent'];
export type ErrorResponse = Schemas['ErrorResponse'];
