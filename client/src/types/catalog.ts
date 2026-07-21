/** Catalog types — mirror the backend CatalogDtos responses. */

export interface Category {
  id: string;
  name: string;
  slug: string;
  description?: string | null;
  imageUrl?: string | null;
  position: number;
  children: Category[];
}

export interface NutritionFacts {
  basisGrams: number;
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
  source: string;
  complete: boolean;
}

export interface ProductImage {
  id: string;
  url: string;
  alt?: string | null;
  position: number;
  isPrimary: boolean;
}

export interface ProductVariant {
  id: string;
  sku: string;
  barcode?: string | null;
  name: string;
  weightGrams: number;
  mrp: number;
  price: number;
  discountPercent: number;
  currency: string;
  isDefault: boolean;
  isActive: boolean;
  /** Units buyable right now (on-hand minus reserved, across warehouses). 0 = out of stock. */
  availableStock: number;
  nutrition: NutritionFacts | null;
  images: ProductImage[];
}

export interface Product {
  id: string;
  name: string;
  slug: string;
  description?: string | null;
  origin?: string | null;
  isOrganic: boolean;
  gstRatePct: number;
  hsnCode?: string | null;
  status: string;
  brand?: { id: string; name: string; slug: string } | null;
  category?: Category | null;
  variants: ProductVariant[];
}

export interface ProductSummary {
  id: string;
  name: string;
  slug: string;
  isOrganic: boolean;
  brandName?: string | null;
  categorySlug?: string | null;
  imageUrl?: string | null;
  price: number;
  mrp: number;
  discountPercent: number;
  sku: string;
}

/** Rich, AI-assisted product content (V11). Fetched separately from the product; null if none published. */
export interface ProductContent {
  overview?: string | null;
  advantages?: string | null;
  healthBenefits?: string | null;
  nutrientSupport?: string | null;
  whyChoose?: string | null;
  storageTips?: string | null;
  status: 'DRAFT' | 'PUBLISHED';
  generatedByAi: boolean;
  aiModel?: string | null;
  generatedAt?: string | null;
  publishedAt?: string | null;
}

/** One complementary item suggested for the cart ("complete your basket"). */
export interface BasketRec {
  variantId: string;
  sku: string;
  productName: string;
  productSlug: string;
  price: number;
  imageUrl?: string | null;
  reason: string;
}

/** An ingredient the recipe needs that we DO stock — addable to the cart. */
export interface RecipeItem {
  ingredient: string;
  variantId: string;
  sku: string;
  productName: string;
  productSlug: string;
  price: number;
}

/** AI "turn my cart into a meal" result. */
export interface RecipeSuggestion {
  dish: string;
  description: string;
  usingFromCart: string[];
  addToCart: RecipeItem[];
  alsoNeed: string[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type CertType =
  | 'NPOP_INDIA_ORGANIC'
  | 'JAIVIK_BHARAT'
  | 'USDA_ORGANIC'
  | 'EU_ORGANIC'
  | 'FSSAI'
  | 'LAB_REPORT'
  | 'OTHER';

/** A product's organic/quality certificate — proof behind the "100% organic" claim. */
export interface Certification {
  id: string;
  certType: CertType;
  issuingBody?: string | null;
  certificateNumber?: string | null;
  documentUrl: string;
  validFrom?: string | null;
  validUntil?: string | null;
  verified: boolean;
  expired: boolean;
}

/** Human labels for certificate types. */
export const CERT_TYPE_LABEL: Record<CertType, string> = {
  NPOP_INDIA_ORGANIC: 'India Organic (NPOP)',
  JAIVIK_BHARAT: 'Jaivik Bharat',
  USDA_ORGANIC: 'USDA Organic',
  EU_ORGANIC: 'EU Organic',
  FSSAI: 'FSSAI',
  LAB_REPORT: 'Lab report',
  OTHER: 'Other',
};
