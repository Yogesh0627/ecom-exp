/** Admin console types — mirror the backend admin/management controllers. */

/** Dashboard summary (GET /admin/dashboard/summary). Aggregates computed in the DB. */
export interface DashboardSummary {
  ordersByStatus: Record<string, number>;
  revenue: number;
  paidOrders: number;
  todayRevenue: number;
  todayOrders: number;
  lowStockAlerts: number;
  pendingReviews: number;
  pendingAdjustments: number;
  activeProducts: number;
  totalCustomers: number;
  /** A captured-money vs order-total mismatch — should always be 0. */
  paymentMismatches: number;
  /** Inventory ledger drift — should always be 0. */
  ledgerDrift: number;
}

export interface TopProduct {
  product: string;
  units: number;
  revenue: number;
}

/** Admin product listing row (GET /admin/products) — status-aware, spans all statuses. */
export interface AdminProductRow {
  id: string;
  name: string;
  slug: string;
  status: string;
  isOrganic: boolean;
  categoryName?: string | null;
  sku?: string | null;
  price?: number | null;
  mrp?: number | null;
  variantCount: number;
}

// ---------- catalog write payloads (mirror CatalogDtos requests) ----------

export interface CreateCategoryPayload {
  name: string;
  slug: string;
  description?: string;
  imageUrl?: string;
  parentId?: string;
  position?: number;
}

export interface NutritionPayload {
  caloriesKcal?: number;
  proteinG?: number;
  fatG?: number;
  carbohydratesG?: number;
  fiberG?: number;
  sugarG?: number;
  ironMg?: number;
  vitaminAMcg?: number;
  vitaminCMg?: number;
  vitaminDMcg?: number;
  potassiumMg?: number;
  sodiumMg?: number;
  source?: string;
  sourceRef?: string;
}

export interface CreateVariantPayload {
  sku: string;
  barcode?: string;
  name: string;
  weightGrams: number;
  mrp: number;
  price: number;
  isDefault?: boolean;
  nutrition?: NutritionPayload;
  imageUrls?: string[];
}

export interface CreateProductPayload {
  name: string;
  slug: string;
  description?: string;
  brandId?: string;
  categoryId: string;
  origin?: string;
  isOrganic?: boolean;
  gstRatePct?: number;
  hsnCode?: string;
  variants: CreateVariantPayload[];
}

export interface UpdateProductPayload {
  name?: string;
  description?: string;
  brandId?: string;
  categoryId?: string;
  origin?: string;
  isOrganic?: boolean;
  gstRatePct?: number;
  hsnCode?: string;
  status?: string;
}

// ---------- inventory ----------

export interface WarehouseRow {
  id: string;
  code: string;
  name: string;
  city?: string | null;
  state?: string | null;
}

export interface CreateWarehousePayload {
  code: string;
  name: string;
  city?: string;
  state?: string;
  pincode?: string;
}

export interface StockRow {
  inventoryId: string;
  warehouseCode: string;
  sku: string;
  onHand: number;
  reserved: number;
  available: number;
  belowReorderPoint: boolean;
}

export interface VariantOption {
  variantId: string;
  sku: string;
  productName: string;
  variantName: string;
  price: number;
}

export interface ReceiveStockPayload {
  variantId: string;
  warehouseId: string;
  lotNo: string;
  qty: number;
  costPrice: number;
  expiryDate?: string;
}

// ---------- suppliers ----------

export interface SupplierRow {
  id: string;
  code: string;
  name: string;
  contactName?: string | null;
  contactPhone?: string | null;
  gstin?: string | null;
  fssaiLicense?: string | null;
  city?: string | null;
  state?: string | null;
  isActive: boolean;
}

export interface CreateSupplierPayload {
  code: string;
  name: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  gstin?: string;
  fssaiLicense?: string;
  addressLine?: string;
  city?: string;
  state?: string;
  pincode?: string;
}

// ---------- purchase orders ----------

export type PoStatus = 'DRAFT' | 'SUBMITTED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';

export interface PoLine {
  poItemId: string;
  sku: string;
  qtyOrdered: number;
  qtyReceived: number;
  outstanding: number;
}

export interface PurchaseOrder {
  id: string;
  poNumber: string;
  status: PoStatus;
  grandTotal: number;
  lines: PoLine[];
}

export interface PoSummary {
  id: string;
  poNumber: string;
  status: PoStatus;
  supplierName: string;
  warehouseCode: string;
  grandTotal: number;
  lineCount: number;
  expectedAt: string;
}

export interface CreatePoLine {
  variantId: string;
  qty: number;
  unitCost: number;
}

export interface CreatePoPayload {
  supplierId: string;
  warehouseId: string;
  expectedAt?: string;
  notes?: string;
  lines: CreatePoLine[];
}

export interface ReceivePoLine {
  poItemId: string;
  qtyReceived: number;
  lotNo?: string;
  expiryDate?: string;
}

/** GST slabs allowed by the backend (products_gst_rate_chk). */
export const GST_RATES = [0, 0.25, 3, 5, 12, 18, 28] as const;

/** Product lifecycle states (ProductStatus). */
export const PRODUCT_STATUSES = ['DRAFT', 'ACTIVE', 'ARCHIVED', 'OUT_OF_STOCK'] as const;

// ---------- reviews moderation ----------

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'FLAGGED';

/** GET /reviews/moderation-queue response. */
export interface ModerationQueue {
  total: number;
  reviews: import('./auth').Review[];
}

// ---------- coupons ----------

export type CouponType = 'PERCENT' | 'FLAT' | 'FREE_SHIPPING';

export interface Coupon {
  id: string;
  code: string;
  description?: string | null;
  type: CouponType;
  value: number;
  maxDiscount?: number | null;
  minCartValue: number;
  validFrom: string;
  validUntil: string;
  maxUses?: number | null;
  timesUsed: number;
  maxUsesPerUser: number;
  firstOrderOnly: boolean;
  isActive: boolean;
}

export interface CreateCouponPayload {
  code: string;
  description?: string;
  type: CouponType;
  value: number;
  maxDiscount?: number;
  minCartValue?: number;
  validFrom: string;
  validUntil: string;
  maxUses?: number;
  maxUsesPerUser?: number;
  firstOrderOnly?: boolean;
}

export const COUPON_TYPES: CouponType[] = ['PERCENT', 'FLAT', 'FREE_SHIPPING'];

// ---------- settings ----------

export interface Setting {
  key: string;
  /** JSON-encoded value (a number, boolean, string, or object as JSON text). */
  value: string;
  description?: string;
  isPublic: boolean;
}

// ---------- AI spend ----------

export interface AiFeatureUsage {
  feature: string;
  calls: number;
  tokensIn: number;
  tokensOut: number;
  costInr: number;
}

export interface AiSpendSummary {
  monthSpendInr: number;
  budgetInr: number | null;
  totalCalls: number;
  byFeature: AiFeatureUsage[];
}

// ---------- banners ----------

export type BannerPlacement =
  | 'HOME_HERO'
  | 'HOME_STRIP'
  | 'CATEGORY_TOP'
  | 'CART_UPSELL'
  | 'CHECKOUT';

export interface AdminBanner {
  id: string;
  title: string;
  subtitle?: string | null;
  imageUrl: string;
  linkUrl?: string | null;
  placement: BannerPlacement;
  position: number;
  isActive: boolean;
  live: boolean;
  activeFrom?: string | null;
  activeUntil?: string | null;
}

export interface CreateBannerPayload {
  title: string;
  subtitle?: string;
  imageUrl: string;
  mobileImageUrl?: string;
  linkUrl?: string;
  placement: BannerPlacement;
  position?: number;
  activeFrom?: string;
  activeUntil?: string;
}

// ---------- certifications (admin write) ----------

import type { CertType } from './catalog';

export interface CreateCertPayload {
  certType: CertType;
  issuingBody?: string;
  certificateNumber?: string;
  documentUrl: string;
  validFrom?: string;
  validUntil?: string;
  notes?: string;
}

export const CERT_TYPES: CertType[] = [
  'NPOP_INDIA_ORGANIC',
  'JAIVIK_BHARAT',
  'USDA_ORGANIC',
  'EU_ORGANIC',
  'FSSAI',
  'LAB_REPORT',
  'OTHER',
];

export const BANNER_PLACEMENTS: BannerPlacement[] = [
  'HOME_HERO',
  'HOME_STRIP',
  'CATEGORY_TOP',
  'CART_UPSELL',
  'CHECKOUT',
];
