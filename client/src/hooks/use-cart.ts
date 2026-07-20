'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '@/api';
import { QUERY_KEYS } from '@/constants';
import { useAuth } from './use-auth';
import type { Cart } from '@/types';

/** The cart requires auth; the query is disabled when logged out so it doesn't 401-loop. */
export function useCart() {
  const { isAuthenticated, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.cart,
    queryFn: cartApi.get,
    enabled: isReady && isAuthenticated,
  });
}

/**
 * Cart mutations all return the fresh cart, so we seed the query cache with the response — the
 * badge and cart page update instantly without a refetch round-trip.
 */
export function useCartMutations() {
  const queryClient = useQueryClient();
  const seed = (cart: Cart) => queryClient.setQueryData(QUERY_KEYS.cart, cart);

  const addItem = useMutation({
    mutationFn: ({ variantId, qty }: { variantId: string; qty: number }) =>
      cartApi.addItem(variantId, qty),
    onSuccess: seed,
  });

  const updateItem = useMutation({
    mutationFn: ({ variantId, qty }: { variantId: string; qty: number }) =>
      cartApi.updateItem(variantId, qty),
    onSuccess: seed,
  });

  const removeItem = useMutation({
    mutationFn: (variantId: string) => cartApi.removeItem(variantId),
    onSuccess: seed,
  });

  return { addItem, updateItem, removeItem };
}
