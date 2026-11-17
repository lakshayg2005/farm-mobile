import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  addItem,
  applyShortages,
  emptyCart,
  estimateTotal,
  groupByFarm,
  removeItem,
  setQty,
  subtotal,
  toOrderItems,
} from '../src/checkout/cart.ts';

const tomato = {
  listingId: 'l-tomato',
  farmId: 'farm-a',
  farmName: 'Green Farm',
  name: 'Tomato',
  unit: 'kg',
  unitPrice: 40,
};
const spinach = {
  listingId: 'l-spinach',
  farmId: 'farm-b',
  farmName: 'Sunrise Farm',
  name: 'Spinach',
  unit: 'bunch',
  unitPrice: 25.5,
};
const onion = { ...tomato, listingId: 'l-onion', name: 'Onion', unitPrice: 30 };

test('adding the same listing twice increases the quantity', () => {
  const cart = addItem(addItem(emptyCart, tomato), { ...tomato, qty: 2.5 });

  assert.equal(cart.items.length, 1);
  assert.equal(cart.items[0].qty, 3.5);
});

test('quantities never go above the known stock', () => {
  let cart = addItem(emptyCart, { ...tomato, qty: 5, maxQty: 3 });
  assert.equal(cart.items[0].qty, 3);

  cart = setQty(cart, 'l-tomato', 10);
  assert.equal(cart.items[0].qty, 3);
});

test('zero or negative quantity removes the line', () => {
  const cart = addItem(emptyCart, tomato);

  assert.deepEqual(setQty(cart, 'l-tomato', 0).items, []);
  assert.deepEqual(setQty(cart, 'l-tomato', -1).items, []);
  assert.deepEqual(removeItem(cart, 'l-tomato').items, []);
});

test('adding a non positive quantity changes nothing', () => {
  assert.equal(addItem(emptyCart, { ...tomato, qty: 0 }), emptyCart);
});

test('the cart is grouped per farm, like the orders it becomes', () => {
  let cart = addItem(emptyCart, { ...tomato, qty: 2 });
  cart = addItem(cart, spinach);
  cart = addItem(cart, onion);

  const groups = groupByFarm(cart);

  assert.deepEqual(
    groups.map((g) => g.farmId),
    ['farm-a', 'farm-b'],
  );
  assert.deepEqual(
    groups[0].items.map((i) => i.name),
    ['Tomato', 'Onion'],
  );
  assert.equal(groups[0].subtotal, 110);
  assert.equal(groups[1].subtotal, 25.5);
});

test('totals add one delivery fee per farm and avoid float noise', () => {
  let cart = addItem(emptyCart, { ...tomato, unitPrice: 0.1, qty: 3 });
  cart = addItem(cart, spinach);

  assert.equal(subtotal(cart), 25.8);
  assert.equal(estimateTotal(cart, 20), 65.8);
});

test('order items sent to the server carry only listing and qty', () => {
  const cart = addItem(emptyCart, { ...tomato, qty: 2 });

  assert.deepEqual(toOrderItems(cart), [{ listingId: 'l-tomato', qty: 2 }]);
});

test('shortages cap quantities and drop sold out lines', () => {
  let cart = addItem(emptyCart, { ...tomato, qty: 5 });
  cart = addItem(cart, { ...spinach, qty: 2 });
  cart = addItem(cart, onion);

  const next = applyShortages(cart, [
    { listingId: 'l-tomato', requested: 5, available: 3 },
    { listingId: 'l-spinach', requested: 2, available: 0 },
  ]);

  assert.deepEqual(
    next.items.map((i) => [i.listingId, i.qty, i.maxQty]),
    [
      ['l-tomato', 3, 3],
      ['l-onion', 1, undefined],
    ],
  );
});
