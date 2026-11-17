/** Cart logic: pure functions on an immutable cart. The cart lives only in the app. */

export type CartItem = {
  listingId: string;
  farmId: string;
  farmName: string;
  name: string;
  unit: string;
  unitPrice: number;
  qty: number;
  /** Stock we last saw for this listing, if known. The server has the final say at checkout. */
  maxQty?: number;
};

export type Cart = { items: CartItem[] };

export type NewCartItem = Omit<CartItem, 'qty'> & { qty?: number };

/** One entry of `error.details` of a 409 STOCK_UNAVAILABLE from the order service. */
export type Shortage = { listingId: string; requested: number; available: number };

export type FarmGroup = { farmId: string; farmName: string; items: CartItem[]; subtotal: number };

export const emptyCart: Cart = { items: [] };

export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function clamp(qty: number, maxQty?: number): number {
  const rounded = Math.round(qty * 1000) / 1000;
  return maxQty !== undefined ? Math.min(rounded, maxQty) : rounded;
}

/** Add a listing, or increase its quantity when it is already in the cart. */
export function addItem(cart: Cart, item: NewCartItem): Cart {
  const qty = item.qty ?? 1;
  if (!(qty > 0)) return cart;
  const existing = cart.items.find((i) => i.listingId === item.listingId);
  if (!existing) {
    const next = clamp(qty, item.maxQty);
    return next > 0 ? { items: [...cart.items, { ...item, qty: next }] } : cart;
  }
  return setQty(cart, item.listingId, existing.qty + qty);
}

/** Set a quantity; zero or less removes the line. Never goes above a known stock. */
export function setQty(cart: Cart, listingId: string, qty: number): Cart {
  if (!(qty > 0)) return removeItem(cart, listingId);
  return {
    items: cart.items.map((i) =>
      i.listingId === listingId ? { ...i, qty: clamp(qty, i.maxQty) } : i,
    ),
  };
}

export function removeItem(cart: Cart, listingId: string): Cart {
  return { items: cart.items.filter((i) => i.listingId !== listingId) };
}

export function itemCount(cart: Cart): number {
  return cart.items.length;
}

export function subtotal(cart: Cart): number {
  return round2(cart.items.reduce((sum, i) => sum + round2(i.unitPrice * i.qty), 0));
}

/** The checkout creates one order per farm, so the cart is shown grouped the same way. */
export function groupByFarm(cart: Cart): FarmGroup[] {
  const groups = new Map<string, FarmGroup>();
  for (const item of cart.items) {
    const group = groups.get(item.farmId) ?? {
      farmId: item.farmId,
      farmName: item.farmName,
      items: [],
      subtotal: 0,
    };
    group.items.push(item);
    group.subtotal = round2(group.subtotal + round2(item.unitPrice * item.qty));
    groups.set(item.farmId, group);
  }
  return [...groups.values()];
}

/** Estimated total: items plus one delivery fee per farm. The server computes the real total. */
export function estimateTotal(cart: Cart, feePerFarm: number): number {
  return round2(subtotal(cart) + feePerFarm * groupByFarm(cart).length);
}

/** Cap quantities at what is left after a STOCK_UNAVAILABLE; sold-out lines are removed. */
export function applyShortages(cart: Cart, shortages: Shortage[]): Cart {
  const available = new Map(shortages.map((s) => [s.listingId, s.available]));
  const items: CartItem[] = [];
  for (const item of cart.items) {
    const left = available.get(item.listingId);
    if (left === undefined) {
      items.push(item);
    } else if (left > 0) {
      items.push({ ...item, qty: Math.min(item.qty, left), maxQty: left });
    }
  }
  return { items };
}

export function toOrderItems(cart: Cart): { listingId: string; qty: number }[] {
  return cart.items.map((i) => ({ listingId: i.listingId, qty: i.qty }));
}
