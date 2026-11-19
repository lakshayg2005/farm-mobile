import type createClient from 'openapi-fetch';
import { createServiceClient } from './client';
import type { components, paths } from './generated/order';

/**
 * Client for the order service, typed with its contract (contracts/openapi/order.json): paths are
 * service-relative (`/orders`), the base URL adds the gateway's `/order` prefix. Regenerate with:
 *   pnpm --filter @farm/api exec openapi-typescript <path to order.json> -o src/generated/order.d.ts
 */
export type OrderClient = ReturnType<typeof createClient<paths>>;
export const orderClient: OrderClient = createServiceClient<paths>('order');

type Schemas = components['schemas'];
export type Checkout = Schemas['Checkout'];
export type CheckoutRequest = Schemas['CheckoutRequest'];
export type DeliveryAddress = Schemas['DeliveryAddress'];
export type Order = Schemas['Order'];
export type OrderStatus = Schemas['OrderStatus'];
export type PaymentIntent = Schemas['PaymentIntent'];
export type ErrorResponse = Schemas['ErrorResponse'];
export type OrderDetail = Schemas['OrderDetail'];
export type OrderSummary = Schemas['OrderSummary'];
export type OrderList = Schemas['OrderList'];
export type OrderRating = Schemas['OrderRating'];
export type PaymentRecord = Schemas['PaymentRecord'];
export type HarvestOrderChoice = Schemas['HarvestOrderChoice'];
export type HarvestOption = Schemas['HarvestOption'];
