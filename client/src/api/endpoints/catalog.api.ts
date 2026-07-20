import { api } from '@/lib';
import type {
  Category,
  Certification,
  PageResponse,
  Product,
  ProductContent,
  ProductSummary,
  Recommendation,
  Review,
} from '@/types';

/** Catalog + recommendations + reviews — the read side of the storefront. */
export const catalogApi = {
  async categories(): Promise<Category[]> {
    const { data } = await api.get<Category[]>('/categories');
    return data;
  },
  async search(q?: string, page = 0, size = 20): Promise<PageResponse<ProductSummary>> {
    const { data } = await api.get<PageResponse<ProductSummary>>('/products', {
      params: { q: q || undefined, page, size },
    });
    return data;
  },
  async byCategory(slug: string, page = 0, size = 20): Promise<PageResponse<ProductSummary>> {
    const { data } = await api.get<PageResponse<ProductSummary>>(`/products/category/${slug}`, {
      params: { page, size },
    });
    return data;
  },
  async product(slug: string): Promise<Product> {
    const { data } = await api.get<Product>(`/products/${slug}`);
    return data;
  },
  async recommendations(variantId: string): Promise<Recommendation[]> {
    const { data } = await api.get<Recommendation[]>(`/recommendations/variant/${variantId}`);
    return data;
  },
  /** "You may also like" — similar in-stock products. */
  async similar(slug: string, limit = 8): Promise<ProductSummary[]> {
    const { data } = await api.get<ProductSummary[]>(`/products/${slug}/similar`, {
      params: { limit },
    });
    return data;
  },
  /** Published rich content for a product; null when none is published (204). */
  async content(slug: string): Promise<ProductContent | null> {
    const res = await api.get<ProductContent | ''>(`/products/${slug}/content`, {
      // 204 has no body — axios returns '' ; normalise to null.
      validateStatus: (s) => s === 200 || s === 204,
    });
    return res.status === 204 || !res.data ? null : (res.data as ProductContent);
  },
  async reviews(productId: string): Promise<Review[]> {
    const { data } = await api.get<Review[]>(`/reviews/product/${productId}`);
    return data;
  },
  async certifications(slug: string): Promise<Certification[]> {
    const { data } = await api.get<Certification[]>(`/products/${slug}/certifications`);
    return data;
  },
};

/** Admin-only rich-content management (generate with AI, edit, publish). */
export type ContentDraft = Pick<
  ProductContent,
  'overview' | 'advantages' | 'healthBenefits' | 'nutrientSupport' | 'whyChoose' | 'storageTips'
>;

export const contentAdminApi = {
  /** Current content in any status (draft or published); null if none exists. */
  async get(slug: string): Promise<ProductContent | null> {
    const res = await api.get<ProductContent | ''>(`/admin/products/${slug}/content`, {
      validateStatus: (s) => s === 200 || s === 204,
    });
    return res.status === 204 || !res.data ? null : (res.data as ProductContent);
  },
  async generate(slug: string): Promise<ProductContent> {
    const { data } = await api.post<ProductContent>(`/admin/products/${slug}/content/generate`);
    return data;
  },
  async update(slug: string, body: ContentDraft): Promise<ProductContent> {
    const { data } = await api.put<ProductContent>(`/admin/products/${slug}/content`, body);
    return data;
  },
  async publish(slug: string): Promise<ProductContent> {
    const { data } = await api.post<ProductContent>(`/admin/products/${slug}/content/publish`);
    return data;
  },
  async unpublish(slug: string): Promise<ProductContent> {
    const { data } = await api.post<ProductContent>(`/admin/products/${slug}/content/unpublish`);
    return data;
  },
};
