/** AI hero-feature types — mirror the backend AI controllers (meal planner, pantry, fridge scan). */

export type MealGoal =
  | 'WEIGHT_LOSS'
  | 'MUSCLE_GAIN'
  | 'MAINTENANCE'
  | 'DIABETIC_FRIENDLY'
  | 'HIGH_PROTEIN'
  | 'BALANCED'
  | 'KIDS';

export interface MealEntry {
  day: number; // 1 = Monday … 7 = Sunday
  mealType: 'BREAKFAST' | 'LUNCH' | 'SNACK' | 'DINNER';
  title: string;
  servings: number;
}

export interface MealPlan {
  id: string;
  goal: string;
  weekStart: string;
  status: string;
  entryCount: number;
  meals: MealEntry[];
}

export type PantryUnit = 'G' | 'KG' | 'ML' | 'L' | 'PIECE' | 'PACK';

export interface PantryItem {
  id: string;
  ingredientName: string;
  qty: number;
  unit: string;
  expiryDate?: string | null;
  expiringSoon: boolean;
}

export interface DetectedItem {
  detectedName: string;
  confidence: number | null;
  quantity?: string | null;
  needsConfirmation: boolean;
  matchedVariantId?: string | null;
  matchedProductName?: string | null;
  matchedPrice?: number | null;
}

export interface FridgeScanResult {
  scanId: string;
  status: string;
  detectedCount: number;
  matchedCount: number;
  items: DetectedItem[];
}

/** Live AI availability (quota/rate-limit aware) from GET /ai/status. */
export interface AiStatus {
  enabled: boolean;
  available: boolean;
  rateLimited: boolean;
  retryAfterSeconds: number;
  message?: string | null;
}
