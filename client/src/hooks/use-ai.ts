'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { aiApi, type AddPantryPayload } from '@/api';
import { QUERY_KEYS } from '@/constants';
import { useAuth } from './use-auth';
import type { MealGoal, RecipeSuggestion } from '@/types';

/** Generate a weekly meal plan (calls Gemini server-side). */
export function useGenerateMealPlan() {
  return useMutation({
    mutationFn: ({ goal, calories }: { goal: MealGoal; calories?: number }) =>
      aiApi.generateMealPlan(goal, calories),
  });
}

export function usePantry() {
  const { isAuthenticated, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.pantry,
    queryFn: aiApi.pantry,
    enabled: isReady && isAuthenticated,
  });
}

export function usePantryMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.pantry });

  const add = useMutation({
    mutationFn: (payload: AddPantryPayload) => aiApi.addPantryItem(payload),
    onSuccess: invalidate,
  });
  const consume = useMutation({
    mutationFn: (id: string) => aiApi.consumePantryItem(id),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: (id: string) => aiApi.removePantryItem(id),
    onSuccess: invalidate,
  });
  return { add, consume, remove };
}

/** Scan a fridge photo (Gemini Vision, server-side). */
export function useFridgeScan() {
  return useMutation({
    mutationFn: ({ imageBase64, mimeType }: { imageBase64: string; mimeType: string }) =>
      aiApi.scanFridge(imageBase64, mimeType),
  });
}

/** Live AI availability (quota/rate-limit aware). Polls so a cooldown clears on its own. */
export function useAiStatus() {
  return useQuery({
    queryKey: QUERY_KEYS.aiStatus,
    queryFn: aiApi.status,
    refetchInterval: 30_000,
    staleTime: 20_000,
  });
}

/** "Complete your basket" — complementary in-stock items for the current cart. */
export function useCartRecommendations(limit = 6) {
  const { isAuthenticated, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.cartRecommendations,
    queryFn: () => aiApi.cartRecommendations(limit),
    enabled: isReady && isAuthenticated,
  });
}

/** AI "turn my cart into a meal" (on-demand; calls Gemini server-side). */
export function useRecipeSuggestion() {
  return useMutation<RecipeSuggestion, Error, { includePantry?: boolean; exclude?: string[] }>({
    mutationFn: ({ includePantry = true, exclude = [] } = {}) =>
      aiApi.recipeSuggestion(includePantry, exclude),
  });
}
