/** Cart + Smart Cart Nutrition types — mirror the backend CartDtos. */

export interface NutrientTotals {
  caloriesKcal: number | null;
  proteinG: number | null;
  fatG: number | null;
  carbohydratesG: number | null;
  fiberG: number | null;
  sugarG: number | null;
  ironMg: number | null;
  vitaminAMcg: number | null;
  vitaminCMg: number | null;
  vitaminDMcg: number | null;
  potassiumMg: number | null;
  sodiumMg: number | null;
}

export interface NutritionWarning {
  level: 'INFO' | 'CAUTION' | 'HIGH';
  nutrient: string;
  message: string;
}

export interface NutritionSummary {
  complete: boolean;
  linesMissingData: string[];
  totalWeightG: number | null;
  totals: NutrientTotals;
  per100g: NutrientTotals | null;
  /** 0–100, or null when the basket has items with no nutrition data. */
  healthScore: number | null;
  scoreBasis: string;
  warnings: NutritionWarning[];
}

export interface CartItem {
  id: string;
  variantId: string;
  sku: string;
  productName: string;
  variantName: string;
  productSlug: string;
  imageUrl?: string | null;
  qty: number;
  unitPrice: number;
  lineTotal: number;
  weightGrams: number;
  priceChanged: boolean;
  priceWhenAdded: number;
  availableStock: number;
}

export interface Cart {
  id: string;
  items: CartItem[];
  totalUnits: number;
  subtotal: number;
  currency: string;
  hasPriceChanges: boolean;
  unavailableVariantIds: string[];
  nutrition: NutritionSummary;
}
