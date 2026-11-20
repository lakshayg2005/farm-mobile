import { useSyncExternalStore } from 'react';
import type { Checkout } from '@farm/api/src/order';
import * as cartLogic from './cart.ts';
import type { Cart, NewCartItem, Shortage } from './cart.ts';

/**
 * In-memory cart shared by every screen. Other screens add produce with `addToCart`
 * (the browse and listing screens, farm-mobile #3).
 * TODO(M1): the cart is lost when the app is closed. Persisting it needs AsyncStorage, which is
 * not a dependency yet; adding it changes the shared lockfile, so it is left for later.
 */
let cart: Cart = cartLogic.emptyCart;
let lastCheckout: Checkout | null = null;
const listeners = new Set<() => void>();

function emit(): void {
  listeners.forEach((listener) => listener());
}

function update(next: Cart): void {
  if (next !== cart) {
    cart = next;
    emit();
  }
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Adds produce to the cart. Offline demo listings have ids like `lst-tomato-hybrid-1`, which the
 * order service rejects (listingId must be a UUID), so they are not allowed into the cart.
 * Returns false when the item was refused.
 */
export function addToCart(item: NewCartItem): boolean {
  if (!UUID.test(item.listingId)) return false;
  update(cartLogic.addItem(cart, item));
  return true;
}
export const setCartQty = (listingId: string, qty: number): void =>
  update(cartLogic.setQty(cart, listingId, qty));
export const removeFromCart = (listingId: string): void =>
  update(cartLogic.removeItem(cart, listingId));
export const clearCart = (): void => update(cartLogic.emptyCart);
export const adjustCartToStock = (shortages: Shortage[]): void =>
  update(cartLogic.applyShortages(cart, shortages));
export const getCart = (): Cart => cart;

export function useCart(): Cart {
  return useSyncExternalStore(subscribe, () => cart);
}

/** The checkout that was just placed, shown on the confirmation screen. */
export function setLastCheckout(checkout: Checkout | null): void {
  lastCheckout = checkout;
  emit();
}

export function useLastCheckout(): Checkout | null {
  return useSyncExternalStore(subscribe, () => lastCheckout);
}
