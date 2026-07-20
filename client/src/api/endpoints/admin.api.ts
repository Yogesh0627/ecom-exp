import { api } from '@/lib';
import type {
  AdminBanner,
  AdminProductRow,
  AiSpendSummary,
  Category,
  Certification,
  Coupon,
  CreateBannerPayload,
  CreateCertPayload,
  CreateCategoryPayload,
  CreateCouponPayload,
  CreateProductPayload,
  CreateWarehousePayload,
  DashboardSummary,
  ModerationQueue,
  CreatePoPayload,
  CreateSupplierPayload,
  Order,
  OrderStatus,
  OrderSummary,
  PageResponse,
  PoStatus,
  PoSummary,
  Product,
  PurchaseOrder,
  ReceivePoLine,
  ReceiveStockPayload,
  ReviewStatus,
  Setting,
  StockRow,
  SupplierRow,
  TopProduct,
  UpdateProductPayload,
  VariantOption,
  WarehouseRow,
} from '@/types';

/**
 * Admin console endpoints. Every call requires a staff permission server-side (@PreAuthorize); the
 * axios instance attaches the JWT, and a 403 surfaces to the caller as a permission error.
 */
export const adminApi = {
  // ---------- files / storage ----------
  /** Upload a file to object storage; returns its key and public URL. */
  uploadFile: async (file: File, category = 'product-images'): Promise<{ key: string; url: string }> => {
    const form = new FormData();
    form.append('file', file);
    form.append('category', category);
    // Let the browser/axios set the multipart boundary; don't force a JSON content-type.
    const { data } = await api.post<{ key: string; url: string }>('/files', form);
    return data;
  },

  dashboardSummary: async (): Promise<DashboardSummary> => {
    const { data } = await api.get<DashboardSummary>('/admin/dashboard/summary');
    return data;
  },
  topProducts: async (limit = 10): Promise<TopProduct[]> => {
    const { data } = await api.get<TopProduct[]>('/admin/dashboard/top-products', {
      params: { limit },
    });
    return data;
  },

  // ---------- categories ----------
  createCategory: async (payload: CreateCategoryPayload): Promise<Category> => {
    const { data } = await api.post<Category>('/categories', payload);
    return data;
  },

  // ---------- products ----------
  searchProducts: async (
    q: string | undefined,
    page = 0,
    size = 20,
    status?: string,
  ): Promise<PageResponse<AdminProductRow>> => {
    const { data } = await api.get<PageResponse<AdminProductRow>>('/admin/products', {
      params: { q: q || undefined, page, size, status: status || undefined },
    });
    return data;
  },
  getProduct: async (slug: string): Promise<Product> => {
    const { data } = await api.get<Product>(`/products/${slug}`);
    return data;
  },
  createProduct: async (payload: CreateProductPayload): Promise<Product> => {
    const { data } = await api.post<Product>('/products', payload);
    return data;
  },
  updateProduct: async (id: string, payload: UpdateProductPayload): Promise<Product> => {
    const { data } = await api.patch<Product>(`/products/${id}`, payload);
    return data;
  },
  deleteProduct: async (id: string): Promise<void> => {
    await api.delete(`/products/${id}`);
  },

  // ---------- orders ----------
  listOrders: async (
    status: OrderStatus | undefined,
    page = 0,
    size = 20,
  ): Promise<PageResponse<OrderSummary>> => {
    const { data } = await api.get<PageResponse<OrderSummary>>('/admin/orders', {
      params: { status: status || undefined, page, size },
    });
    return data;
  },
  getOrder: async (id: string): Promise<Order> => {
    const { data } = await api.get<Order>(`/orders/${id}`);
    return data;
  },
  transitionOrder: async (id: string, status: OrderStatus, note?: string): Promise<Order> => {
    const { data } = await api.post<Order>(`/orders/${id}/status`, { status, note });
    return data;
  },

  // ---------- inventory ----------
  listWarehouses: async (): Promise<WarehouseRow[]> => {
    const { data } = await api.get<WarehouseRow[]>('/inventory/warehouses');
    return data;
  },
  createWarehouse: async (payload: CreateWarehousePayload): Promise<string> => {
    const { data } = await api.post<string>('/inventory/warehouses', payload);
    return data;
  },
  lowStock: async (): Promise<StockRow[]> => {
    const { data } = await api.get<StockRow[]>('/inventory/low-stock');
    return data;
  },
  ledgerDrift: async (): Promise<unknown[]> => {
    const { data } = await api.get<unknown[]>('/inventory/ledger-drift');
    return data;
  },
  searchVariants: async (q: string): Promise<VariantOption[]> => {
    const { data } = await api.get<VariantOption[]>('/admin/products/variants', { params: { q } });
    return data;
  },
  /** Ensure the variant is stocked at the warehouse (idempotent), then receive the lot. */
  receiveStock: async (payload: ReceiveStockPayload): Promise<void> => {
    await api.post('/inventory/stock-item', {
      variantId: payload.variantId,
      warehouseId: payload.warehouseId,
      qty: 10,
    });
    await api.post('/inventory/receive', payload);
  },

  // ---------- suppliers ----------
  listSuppliers: async (): Promise<SupplierRow[]> => {
    const { data } = await api.get<SupplierRow[]>('/inventory/suppliers');
    return data;
  },
  createSupplier: async (payload: CreateSupplierPayload): Promise<SupplierRow> => {
    const { data } = await api.post<SupplierRow>('/inventory/suppliers', payload);
    return data;
  },

  // ---------- purchase orders ----------
  listPurchaseOrders: async (
    status: PoStatus | undefined,
    page = 0,
    size = 20,
  ): Promise<PageResponse<PoSummary>> => {
    const { data } = await api.get<PageResponse<PoSummary>>('/inventory/purchase-orders', {
      params: { status: status || undefined, page, size },
    });
    return data;
  },
  getPurchaseOrder: async (id: string): Promise<PurchaseOrder> => {
    const { data } = await api.get<PurchaseOrder>(`/inventory/purchase-orders/${id}`);
    return data;
  },
  createPurchaseOrder: async (payload: CreatePoPayload): Promise<PurchaseOrder> => {
    const { data } = await api.post<PurchaseOrder>('/inventory/purchase-orders', payload);
    return data;
  },
  submitPurchaseOrder: async (id: string): Promise<PurchaseOrder> => {
    const { data } = await api.post<PurchaseOrder>(`/inventory/purchase-orders/${id}/submit`, {});
    return data;
  },
  receivePurchaseOrder: async (id: string, receipts: ReceivePoLine[]): Promise<PurchaseOrder> => {
    const { data } = await api.post<PurchaseOrder>(`/inventory/purchase-orders/${id}/receive`, {
      receipts,
    });
    return data;
  },
  cancelPurchaseOrder: async (id: string, reason?: string): Promise<PurchaseOrder> => {
    const { data } = await api.post<PurchaseOrder>(`/inventory/purchase-orders/${id}/cancel`, {
      reason,
    });
    return data;
  },

  // ---------- certifications ----------
  addCertification: async (productId: string, payload: CreateCertPayload): Promise<Certification> => {
    const { data } = await api.post<Certification>(
      `/admin/products/${productId}/certifications`,
      payload,
    );
    return data;
  },
  verifyCertification: async (id: string): Promise<Certification> => {
    const { data } = await api.post<Certification>(`/admin/certifications/${id}/verify`, {});
    return data;
  },
  deleteCertification: async (id: string): Promise<void> => {
    await api.delete(`/admin/certifications/${id}`);
  },

  // ---------- reviews moderation ----------
  moderationQueue: async (status: ReviewStatus = 'PENDING'): Promise<ModerationQueue> => {
    const { data } = await api.get<ModerationQueue>('/reviews/moderation-queue', {
      params: { status },
    });
    return data;
  },
  moderateReview: async (id: string, decision: ReviewStatus, note?: string): Promise<void> => {
    await api.post(`/reviews/${id}/moderate`, { decision, note });
  },

  // ---------- coupons ----------
  listCoupons: async (): Promise<Coupon[]> => {
    const { data } = await api.get<Coupon[]>('/coupons');
    return data;
  },
  createCoupon: async (payload: CreateCouponPayload): Promise<{ id: string; code: string }> => {
    const { data } = await api.post<{ id: string; code: string }>('/coupons', payload);
    return data;
  },

  // ---------- settings ----------
  listSettings: async (): Promise<Setting[]> => {
    const { data } = await api.get<Setting[]>('/settings');
    return data;
  },
  updateSetting: async (key: string, value: string): Promise<void> => {
    await api.put(`/settings/${key}`, { value });
  },

  // ---------- AI spend ----------
  aiSpend: async (): Promise<AiSpendSummary> => {
    const { data } = await api.get<AiSpendSummary>('/admin/ai-spend');
    return data;
  },

  // ---------- banners ----------
  listBanners: async (): Promise<AdminBanner[]> => {
    const { data } = await api.get<AdminBanner[]>('/banners/all');
    return data;
  },
  createBanner: async (payload: CreateBannerPayload): Promise<AdminBanner> => {
    const { data } = await api.post<AdminBanner>('/banners', payload);
    return data;
  },
  deleteBanner: async (id: string): Promise<void> => {
    await api.delete(`/banners/${id}`);
  },
};
