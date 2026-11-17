# Cart and checkout (farm-mobile #4)

Screens: `app/cart.tsx`, `app/checkout.tsx`, `app/order-placed.tsx`. Logic is in plain TypeScript
files here so it can be tested without a device (`pnpm --filter @farm/customer test`).

| File              | What                                                                             |
| ----------------- | -------------------------------------------------------------------------------- |
| `cart.ts`         | Immutable cart: add, quantity, per-farm grouping, totals, capping to stock       |
| `cartStore.ts`    | In-memory cart shared by all screens. Other screens add produce with `addToCart` |
| `checkoutFlow.ts` | `CheckoutSession`: keeps the Idempotency-Key and payment intent between taps     |
| `errors.ts`       | Turns the order service's errors into something a screen can act on              |
| `address.ts`      | Delivery address form and validation (same rules as the server)                  |
| `api.ts`          | The two real calls: create payment intent, `POST /orders`                        |

## How a checkout behaves

- The cart lives only in the app. `POST /orders` gets the items, the address and a payment intent,
  and returns one order per farm.
- One `Idempotency-Key` per checkout attempt. A retry after a network error reuses the key and the
  payment intent, so it can never create a second order. Changing the cart or the address, a
  declined payment, or a success starts a new attempt.
- `STOCK_UNAVAILABLE` (409): the screen lists what is left and "Update my cart" caps the quantities.
- `PAYMENT_FAILED` (402): the customer can try again (new key and intent).
- Payments are simulated by the backend; no real money moves.

## Using it from other screens

```ts
import { addToCart } from '../src/checkout';

addToCart({ listingId, farmId, farmName, name, unit, unitPrice, qty: 1, maxQty: qtyAvailable });
```

## Try it without the backend

```bash
pnpm mock <path to farm-backend/contracts/openapi/order.json>   # Prism on :4010
API_BASE_URL=http://localhost:4010 pnpm --filter @farm/customer start
```

## Assumptions (TODO(M1) in the code)

- Login route is `/login` and the order list is `/orders` (farm-mobile #2 and #5 are not merged).
- The delivery location is typed in; it should come from the device location once the browse screen
  adds `expo-location`.
- The cart is not saved when the app closes (needs AsyncStorage, a new shared dependency).
- The delivery fee shown (₹20 per farm) mirrors the backend default; the server total is the truth.
