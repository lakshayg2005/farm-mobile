import { useCallback, useRef, useState } from 'react';
import type { Checkout, DeliveryAddress } from '@farm/api/src/order';
import { createCheckoutApi } from './api.ts';
import type { Cart } from './cart.ts';
import { clearCart, setLastCheckout } from './cartStore.ts';
import { CheckoutSession } from './checkoutFlow.ts';
import type { CheckoutError } from './errors.ts';

/** Flat delivery fee per farm order that the backend charges (order service setting). */
export const DELIVERY_FEE_PER_ORDER = 20;

type State = { status: 'idle' | 'submitting' } | { status: 'error'; error: CheckoutError };

export function useCheckout() {
  const session = useRef(new CheckoutSession(createCheckoutApi(), DELIVERY_FEE_PER_ORDER)).current;
  const [state, setState] = useState<State>({ status: 'idle' });

  const submit = useCallback(
    async (cart: Cart, address: DeliveryAddress, notes?: string): Promise<Checkout | null> => {
      setState({ status: 'submitting' });
      const result = await session.submit(cart, address, notes);
      if (result.ok) {
        setLastCheckout(result.data);
        clearCart();
        setState({ status: 'idle' });
        return result.data;
      }
      setState({ status: 'error', error: result.error });
      return null;
    },
    [session],
  );

  const dismissError = useCallback(() => setState({ status: 'idle' }), []);

  return {
    submit,
    dismissError,
    submitting: state.status === 'submitting',
    error: state.status === 'error' ? state.error : null,
  };
}
