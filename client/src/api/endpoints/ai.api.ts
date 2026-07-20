import { api } from '@/lib';
import type {
  AiStatus,
  BasketRec,
  FridgeScanResult,
  MealGoal,
  MealPlan,
  PantryItem,
  PantryUnit,
  RecipeSuggestion,
} from '@/types';

export interface AddPantryPayload {
  ingredientName: string;
  qty?: number;
  unit?: PantryUnit;
  expiryDate?: string;
}

/**
 * AI hero features (PRD §5). The meal planner and fridge scan call Gemini server-side and can
 * return a 503 when the AI is unavailable — callers surface that as "try again", never a crash.
 */
export const aiApi = {
  /** Live AI availability (quota/rate-limit aware). Public. */
  async status(): Promise<AiStatus> {
    const { data } = await api.get<AiStatus>('/ai/status');
    return data;
  },

  async generateMealPlan(goal: MealGoal, targetCaloriesPerDay?: number): Promise<MealPlan> {
    const { data } = await api.post<MealPlan>('/meal-plans/generate', {
      goal,
      targetCaloriesPerDay,
    });
    return data;
  },

  async pantry(): Promise<PantryItem[]> {
    const { data } = await api.get<PantryItem[]>('/pantry');
    return data;
  },
  async addPantryItem(payload: AddPantryPayload): Promise<PantryItem> {
    const { data } = await api.post<PantryItem>('/pantry/items', payload);
    return data;
  },
  async consumePantryItem(id: string): Promise<void> {
    await api.post(`/pantry/items/${id}/consume`);
  },
  async removePantryItem(id: string): Promise<void> {
    await api.delete(`/pantry/items/${id}`);
  },

  async scanFridge(imageBase64: string, mimeType: string): Promise<FridgeScanResult> {
    const { data } = await api.post<FridgeScanResult>('/fridge-scans', { imageBase64, mimeType });
    return data;
  },

  /** "Complete your basket" — complementary in-stock items for the cart. */
  async cartRecommendations(limit = 6): Promise<BasketRec[]> {
    const { data } = await api.get<BasketRec[]>('/cart/recommendations', { params: { limit } });
    return data;
  },
  /**
   * AI "turn my cart into a meal" — dish + addable ingredients (real in-stock SKUs).
   * `exclude` lists dishes already shown so "Suggest another" returns something different.
   */
  async recipeSuggestion(includePantry = true, exclude: string[] = []): Promise<RecipeSuggestion> {
    const { data } = await api.post<RecipeSuggestion>('/cart/recipe-suggestion', null, {
      params: { includePantry, exclude: exclude.length ? exclude.join(',') : undefined },
    });
    return data;
  },
};
