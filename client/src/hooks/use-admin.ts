'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/api';
import { QUERY_KEYS } from '@/constants';
import { isStaff } from '@/lib';
import { useAuth } from './use-auth';
import type {
  CreateBannerPayload,
  CreateCategoryPayload,
  CreateCertPayload,
  CreateCouponPayload,
  CreatePoPayload,
  CreateProductPayload,
  CreateSupplierPayload,
  CreateWarehousePayload,
  OrderStatus,
  PoStatus,
  ReceivePoLine,
  ReceiveStockPayload,
  ReviewStatus,
  UpdateProductPayload,
} from '@/types';

/** Dashboard summary — enabled only for staff so a customer session never fires an admin 403. */
export function useDashboardSummary() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminDashboardSummary,
    queryFn: adminApi.dashboardSummary,
    enabled: isReady && isStaff(user),
  });
}

export function useTopProducts(limit = 10) {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminTopProducts(limit),
    queryFn: () => adminApi.topProducts(limit),
    enabled: isReady && isStaff(user),
  });
}

// ---------- categories ----------

export function useCreateCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCategoryPayload) => adminApi.createCategory(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.categories }),
  });
}

// ---------- products ----------

export function useAdminProducts(q: string, page: number, size = 20) {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminProducts(q, page),
    queryFn: () => adminApi.searchProducts(q, page, size),
    enabled: isReady && isStaff(user),
    placeholderData: keepPreviousData,
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateProductPayload) => adminApi.createProduct(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
  });
}

export function useUpdateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateProductPayload }) =>
      adminApi.updateProduct(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
  });
}

export function useDeleteProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminApi.deleteProduct(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
  });
}

// ---------- orders ----------

export function useAdminOrders(status: OrderStatus | '', page: number, size = 20) {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminOrders(status, page),
    queryFn: () => adminApi.listOrders(status || undefined, page, size),
    enabled: isReady && isStaff(user),
    placeholderData: keepPreviousData,
  });
}

export function useAdminOrder(id: string) {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminOrder(id),
    queryFn: () => adminApi.getOrder(id),
    enabled: isReady && isStaff(user) && !!id,
  });
}

export function useTransitionOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, note }: { id: string; status: OrderStatus; note?: string }) =>
      adminApi.transitionOrder(id, status, note),
    onSuccess: (order) => {
      queryClient.setQueryData(QUERY_KEYS.adminOrder(order.id), order);
      queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
    },
  });
}

// ---------- inventory ----------

export function useWarehouses() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminWarehouses,
    queryFn: adminApi.listWarehouses,
    enabled: isReady && isStaff(user),
  });
}

export function useCreateWarehouse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateWarehousePayload) => adminApi.createWarehouse(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminWarehouses }),
  });
}

export function useLowStock() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminLowStock,
    queryFn: adminApi.lowStock,
    enabled: isReady && isStaff(user),
  });
}

export function useLedgerDrift() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminLedgerDrift,
    queryFn: adminApi.ledgerDrift,
    enabled: isReady && isStaff(user),
  });
}

/** Debounced variant picker search — enabled once the term is meaningful. */
export function useVariantSearch(term: string) {
  const { user, isReady } = useAuth();
  const q = term.trim();
  return useQuery({
    queryKey: QUERY_KEYS.adminVariantSearch(q),
    queryFn: () => adminApi.searchVariants(q),
    enabled: isReady && isStaff(user) && q.length >= 2,
    placeholderData: keepPreviousData,
  });
}

export function useReceiveStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReceiveStockPayload) => adminApi.receiveStock(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminLowStock }),
  });
}

// ---------- files ----------

export function useUploadFile() {
  return useMutation({
    mutationFn: ({ file, category }: { file: File; category?: string }) =>
      adminApi.uploadFile(file, category),
  });
}

// ---------- certifications ----------

export function useAddCertification(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, payload }: { productId: string; payload: CreateCertPayload }) =>
      adminApi.addCertification(productId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.certifications(slug) }),
  });
}

export function useVerifyCertification(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminApi.verifyCertification(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.certifications(slug) }),
  });
}

export function useDeleteCertification(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminApi.deleteCertification(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.certifications(slug) }),
  });
}

// ---------- suppliers ----------

export function useSuppliers() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminSuppliers,
    queryFn: adminApi.listSuppliers,
    enabled: isReady && isStaff(user),
  });
}

export function useCreateSupplier() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateSupplierPayload) => adminApi.createSupplier(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminSuppliers }),
  });
}

// ---------- purchase orders ----------

export function usePurchaseOrders(status: PoStatus | '', page: number) {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminPurchaseOrders(status, page),
    queryFn: () => adminApi.listPurchaseOrders(status || undefined, page),
    enabled: isReady && isStaff(user),
    placeholderData: keepPreviousData,
  });
}

export function usePurchaseOrder(id: string) {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminPurchaseOrder(id),
    queryFn: () => adminApi.getPurchaseOrder(id),
    enabled: isReady && isStaff(user) && !!id,
  });
}

export function useCreatePurchaseOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreatePoPayload) => adminApi.createPurchaseOrder(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'pos'] }),
  });
}

function invalidatePo(queryClient: ReturnType<typeof useQueryClient>, id: string) {
  queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminPurchaseOrder(id) });
  queryClient.invalidateQueries({ queryKey: ['admin', 'pos'] });
  queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminLowStock });
}

export function useSubmitPurchaseOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminApi.submitPurchaseOrder(id),
    onSuccess: (po) => invalidatePo(queryClient, po.id),
  });
}

export function useReceivePurchaseOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, receipts }: { id: string; receipts: ReceivePoLine[] }) =>
      adminApi.receivePurchaseOrder(id, receipts),
    onSuccess: (po) => invalidatePo(queryClient, po.id),
  });
}

export function useCancelPurchaseOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      adminApi.cancelPurchaseOrder(id, reason),
    onSuccess: (po) => invalidatePo(queryClient, po.id),
  });
}

// ---------- reviews moderation ----------

export function useModerationQueue(status: ReviewStatus) {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminModerationQueue(status),
    queryFn: () => adminApi.moderationQueue(status),
    enabled: isReady && isStaff(user),
  });
}

export function useModerateReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decision, note }: { id: string; decision: ReviewStatus; note?: string }) =>
      adminApi.moderateReview(id, decision, note),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'moderation'] }),
  });
}

// ---------- coupons ----------

export function useCoupons() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminCoupons,
    queryFn: adminApi.listCoupons,
    enabled: isReady && isStaff(user),
  });
}

export function useCreateCoupon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCouponPayload) => adminApi.createCoupon(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminCoupons }),
  });
}

// ---------- settings ----------

export function useSettings() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminSettings,
    queryFn: adminApi.listSettings,
    enabled: isReady && isStaff(user),
  });
}

export function useUpdateSetting() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      adminApi.updateSetting(key, value),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminSettings }),
  });
}

// ---------- AI spend ----------

export function useAiSpend() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminAiSpend,
    queryFn: adminApi.aiSpend,
    enabled: isReady && isStaff(user),
  });
}

// ---------- banners ----------

export function useAdminBanners() {
  const { user, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.adminBanners,
    queryFn: adminApi.listBanners,
    enabled: isReady && isStaff(user),
  });
}

export function useCreateBanner() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateBannerPayload) => adminApi.createBanner(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminBanners }),
  });
}

export function useDeleteBanner() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminApi.deleteBanner(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminBanners }),
  });
}
