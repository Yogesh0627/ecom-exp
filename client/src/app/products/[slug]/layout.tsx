import type { Metadata } from 'next';
import { cache } from 'react';
import { API_BASE_URL, SITE_URL, APP_NAME, CURRENCY } from '@/constants';
import type { Product } from '@/types';

// cache() dedupes the fetch so generateMetadata and the JSON-LD body share a single request.
const getProduct = cache(async (slug: string): Promise<Product | null> => {
  try {
    const res = await fetch(`${API_BASE_URL}/products/${encodeURIComponent(slug)}`, {
      next: { revalidate: 300 },
    });
    return res.ok ? ((await res.json()) as Product) : null;
  } catch {
    return null;
  }
});

function primaryImage(product: Product): string | undefined {
  const v = product.variants?.find((x) => x.isDefault) ?? product.variants?.[0];
  return v?.images?.find((i) => i.isPrimary)?.url ?? v?.images?.[0]?.url;
}

export async function generateMetadata({
  params,
}: {
  params: { slug: string };
}): Promise<Metadata> {
  const product = await getProduct(params.slug);
  if (!product) {
    return { title: 'Product not found' };
  }
  const image = primaryImage(product);
  const description = (
    product.description || `${product.name} — certified organic, delivered fresh across India.`
  ).slice(0, 160);

  return {
    title: product.name,
    description,
    alternates: { canonical: `/products/${product.slug}` },
    openGraph: {
      type: 'website',
      title: `${product.name} · ${APP_NAME}`,
      description,
      url: `${SITE_URL}/products/${product.slug}`,
      images: image ? [{ url: image, alt: product.name }] : undefined,
    },
    twitter: {
      card: 'summary_large_image',
      title: product.name,
      description,
      images: image ? [image] : undefined,
    },
  };
}

export default async function ProductLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: { slug: string };
}) {
  const product = await getProduct(params.slug);
  const variant = product?.variants?.find((v) => v.isDefault) ?? product?.variants?.[0];
  const image = product ? primaryImage(product) : undefined;

  // Product structured data → rich snippets (price, availability) in search results.
  const jsonLd = product
    ? {
        '@context': 'https://schema.org',
        '@type': 'Product',
        name: product.name,
        description: product.description ?? undefined,
        image: image ? [image] : undefined,
        brand: { '@type': 'Brand', name: product.brand?.name ?? APP_NAME },
        ...(variant
          ? {
              sku: variant.sku,
              offers: {
                '@type': 'Offer',
                price: variant.price,
                priceCurrency: variant.currency ?? CURRENCY,
                availability: 'https://schema.org/InStock',
                url: `${SITE_URL}/products/${product.slug}`,
              },
            }
          : {}),
      }
    : null;

  return (
    <>
      {jsonLd && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      )}
      {children}
    </>
  );
}
