import type { Metadata } from 'next';
import { cache } from 'react';
import { API_BASE_URL, SITE_URL, APP_NAME } from '@/constants';
import type { Category } from '@/types';

const getCategory = cache(async (slug: string): Promise<Category | null> => {
  try {
    const res = await fetch(`${API_BASE_URL}/categories/${encodeURIComponent(slug)}`, {
      next: { revalidate: 600 },
    });
    return res.ok ? ((await res.json()) as Category) : null;
  } catch {
    return null;
  }
});

export async function generateMetadata({
  params,
}: {
  params: { slug: string };
}): Promise<Metadata> {
  const category = await getCategory(params.slug);
  if (!category) {
    return { title: 'Category' };
  }
  const description = (
    category.description || `Shop ${category.name} — certified organic, delivered fresh across India.`
  ).slice(0, 160);

  return {
    title: category.name,
    description,
    alternates: { canonical: `/category/${category.slug}` },
    openGraph: {
      type: 'website',
      title: `${category.name} · ${APP_NAME}`,
      description,
      url: `${SITE_URL}/category/${category.slug}`,
      images: category.imageUrl ? [{ url: category.imageUrl, alt: category.name }] : undefined,
    },
  };
}

export default function CategoryLayout({ children }: { children: React.ReactNode }) {
  return children;
}
