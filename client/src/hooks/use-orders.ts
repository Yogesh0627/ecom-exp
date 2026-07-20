'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  orderApi,
  type CheckoutPayload,
  type CreateAddressPayload,
  type OrderBucket,
} from '@/api';
import { QUERY_KEYS } from '@/constants';
import { useAuth } from './use-auth';

export function useAddresses() {
  const { isAuthenticated, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.addresses,
    queryFn: orderApi.addresses,
    enabled: isReady && isAuthenticated,
  });
}

export function useCreateAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateAddressPayload) => orderApi.createAddress(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.addresses }),
  });
}

/** Address edit / delete / set-default — all invalidate the address list. */
export function useAddressMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.addresses });
  const update = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: CreateAddressPayload }) =>
      orderApi.updateAddress(id, payload),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: (id: string) => orderApi.deleteAddress(id),
    onSuccess: invalidate,
  });
  const setDefault = useMutation({
    mutationFn: (id: string) => orderApi.setDefaultAddress(id),
    onSuccess: invalidate,
  });
  return { update, remove, setDefault };
}

export function useMyOrders(bucket: OrderBucket = 'all', page = 0) {
  const { isAuthenticated, isReady } = useAuth();
  return useQuery({
    // Bucket + page in the key so tab/page switches refetch; invalidating ['orders'] clears all.
    queryKey: [...QUERY_KEYS.orders, bucket, page],
    queryFn: () => orderApi.myOrders(bucket, page),
    enabled: isReady && isAuthenticated,
  });
}

/** Cancel one of my orders (only allowed before it ships). */
export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) => orderApi.cancel(id, reason),
    onSuccess: (_data, { id }) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.orders });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.order(id) });
    },
  });
}

export function useOrder(id: string | undefined) {
  return useQuery({
    queryKey: QUERY_KEYS.order(id ?? ''),
    queryFn: () => orderApi.order(id as string),
    enabled: !!id,
  });
}

export function useCheckout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CheckoutPayload) => orderApi.checkout(payload),
    onSuccess: () => {
      // The cart is consumed by checkout, and the orders list changes.
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.orders });
    },
  });
}
