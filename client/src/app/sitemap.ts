import type { MetadataRoute } from 'next';
import { SITE_URL, API_BASE_URL, ROUTES } from '@/constants';
import type { Category } from '@/types';
import { getBlogSummaries } from '@/lib/blog';

// Refetched hourly so new products/categories appear without a redeploy.
export const revalidate = 3600;

function flattenCategories(cats: Category[]): Category[] {
  return cats.flatMap((c) => [c, ...flattenCategories(c.children ?? [])]);
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticPaths: MetadataRoute.Sitemap = [
    { url: `${SITE_URL}`, changeFrequency: 'daily', priority: 1 },
    { url: `${SITE_URL}/meal-planner`, changeFrequency: 'weekly', priority: 0.6 },
    { url: `${SITE_URL}/smart-fridge`, changeFrequency: 'weekly', priority: 0.6 },
    { url: `${SITE_URL}${ROUTES.blog}`, changeFrequency: 'weekly', priority: 0.6 },
    { url: `${SITE_URL}/login`, changeFrequency: 'yearly', priority: 0.3 },
    { url: `${SITE_URL}/register`, changeFrequency: 'yearly', priority: 0.3 },
  ];

  // Blog posts are filesystem-backed, so this never fails the way the API fetch can.
  const blogPaths: MetadataRoute.Sitemap = (await getBlogSummaries().catch(() => [])).map((p) => ({
    url: `${SITE_URL}${ROUTES.blogPost(p.slug)}`,
    lastModified: p.date ? new Date(p.date) : undefined,
    changeFrequency: 'monthly',
    priority: 0.5,
  }));

  try {
    const [categories, products] = await Promise.all([
      fetch(`${API_BASE_URL}/categories`, { next: { revalidate } })
        .then((r) => (r.ok ? (r.json() as Promise<Category[]>) : []))
        .catch(() => [] as Category[]),
      fetch(`${API_BASE_URL}/products?size=500`, { next: { revalidate } })
        .then((r) => (r.ok ? r.json() : { content: [] }))
        .catch(() => ({ content: [] as { slug: string }[] })),
    ]);

    const categoryPaths: MetadataRoute.Sitemap = flattenCategories(categories).map((c) => ({
      url: `${SITE_URL}/category/${c.slug}`,
      changeFrequency: 'weekly',
      priority: 0.7,
    }));

    const productPaths: MetadataRoute.Sitemap = (products.content ?? []).map(
      (p: { slug: string }) => ({
        url: `${SITE_URL}/products/${p.slug}`,
        changeFrequency: 'weekly',
        priority: 0.8,
      }),
    );

    return [...staticPaths, ...blogPaths, ...categoryPaths, ...productPaths];
  } catch {
    // API unreachable at build time — the static + blog routes still make a valid sitemap.
    return [...staticPaths, ...blogPaths];
  }
}
