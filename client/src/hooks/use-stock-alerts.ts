'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { stockAlertApi } from '@/api';
import { QUERY_KEYS } from '@/constants';
import { useAuth } from './use-auth';

/** Variant ids the signed-in user has asked to be notified about. Disabled when logged out. */
export function useStockAlerts() {
  const { isAuthenticated, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.stockAlerts,
    queryFn: stockAlertApi.mine,
    enabled: isReady && isAuthenticated,
    staleTime: 60_000,
  });
}

export function useStockAlertMutations() {
  const queryClient = useQueryClient();
  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: QUERY_KEYS.stockAlerts });

  const subscribe = useMutation({
    mutationFn: (variantId: string) => stockAlertApi.subscribe(variantId),
    onSuccess: invalidate,
  });

  const unsubscribe = useMutation({
    mutationFn: (variantId: string) => stockAlertApi.unsubscribe(variantId),
    onSuccess: invalidate,
  });

  return { subscribe, unsubscribe };
}
