'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { catalogApi, contentApi, contentAdminApi, type ContentDraft } from '@/api';
import { QUERY_KEYS } from '@/constants';

export function useCategories() {
  return useQuery({ queryKey: QUERY_KEYS.categories, queryFn: catalogApi.categories });
}

/** A product's certificates — public, used on the storefront product page and the admin cert view. */
export function useProductCertifications(slug: string, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.certifications(slug),
    queryFn: () => catalogApi.certifications(slug),
    enabled: enabled && !!slug,
  });
}

export function useProductSearch(q?: string, page = 0) {
  return useQuery({
    queryKey: QUERY_KEYS.products({ q, page }),
    queryFn: () => catalogApi.search(q, page),
  });
}

export function useProductsByCategory(slug: string, page = 0) {
  return useQuery({
    queryKey: [...QUERY_KEYS.productsByCategory(slug), page],
    queryFn: () => catalogApi.byCategory(slug, page),
    enabled: !!slug,
  });
}

export function useProduct(slug: string) {
  return useQuery({
    queryKey: QUERY_KEYS.product(slug),
    queryFn: () => catalogApi.product(slug),
    enabled: !!slug,
  });
}

/** Published rich content (advantages, health benefits, …). Null if none published. */
export function useProductContent(slug: string) {
  return useQuery({
    queryKey: QUERY_KEYS.productContent(slug),
    queryFn: () => catalogApi.content(slug),
    enabled: !!slug,
  });
}

/** "You may also like" — similar in-stock products. */
export function useSimilarProducts(slug: string, limit = 8) {
  return useQuery({
    queryKey: QUERY_KEYS.similarProducts(slug),
    queryFn: () => catalogApi.similar(slug, limit),
    enabled: !!slug,
  });
}

/** Admin: current content in any status + generate/edit/publish mutations. */
export function useAdminContent(slug: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: QUERY_KEYS.adminProductContent(slug) });
    qc.invalidateQueries({ queryKey: QUERY_KEYS.productContent(slug) });
  };

  const content = useQuery({
    queryKey: QUERY_KEYS.adminProductContent(slug),
    queryFn: () => contentAdminApi.get(slug),
    enabled: !!slug,
  });
  const generate = useMutation({
    mutationFn: () => contentAdminApi.generate(slug),
    onSuccess: invalidate,
  });
  const update = useMutation({
    mutationFn: (body: ContentDraft) => contentAdminApi.update(slug, body),
    onSuccess: invalidate,
  });
  const publish = useMutation({
    mutationFn: () => contentAdminApi.publish(slug),
    onSuccess: invalidate,
  });
  const unpublish = useMutation({
    mutationFn: () => contentAdminApi.unpublish(slug),
    onSuccess: invalidate,
  });
  return { content, generate, update, publish, unpublish };
}

export function useRecommendations(variantId: string | undefined) {
  return useQuery({
    queryKey: QUERY_KEYS.recommendations(variantId ?? ''),
    queryFn: () => catalogApi.recommendations(variantId as string),
    enabled: !!variantId,
  });
}

export function useReviews(productId: string | undefined) {
  return useQuery({
    queryKey: QUERY_KEYS.reviews(productId ?? ''),
    queryFn: () => catalogApi.reviews(productId as string),
    enabled: !!productId,
  });
}

export function useBanners(placement = 'HOME_HERO') {
  return useQuery({
    queryKey: QUERY_KEYS.banners(placement),
    queryFn: () => contentApi.banners(placement),
  });
}
