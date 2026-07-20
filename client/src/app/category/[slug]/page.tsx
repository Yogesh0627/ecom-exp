'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { ROUTES, categoryEmoji } from '@/constants';
import { useProductsByCategory, useCategories } from '@/hooks';
import { ProductCard } from '@/components/product/product-card';
import { Card, CardContent, Skeleton, Pagination, Breadcrumbs } from '@/components/ui';
import type { Category } from '@/types';

/** Flattens the category tree into a single ordered list (parents then their children). */
function flatten(cats: Category[]): Category[] {
  return cats.flatMap((c) => [c, ...flatten(c.children ?? [])]);
}

export default function CategoryPage() {
  const { slug } = useParams<{ slug: string }>();
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [slug]);

  const { data, isLoading } = useProductsByCategory(slug, page);
  const { data: categoryTree } = useCategories();

  const allCategories = useMemo(() => (categoryTree ? flatten(categoryTree) : []), [categoryTree]);
  const current = allCategories.find((c) => c.slug === slug);
  const title = current?.name ?? slug.replace(/-/g, ' ');

  return (
    <div className="container space-y-6 py-8">
      <Breadcrumbs items={[{ label: 'Shop', href: ROUTES.home }, { label: title }]} />

      <div className="flex items-center gap-3">
        <span className="text-3xl" aria-hidden>
          {categoryEmoji(slug)}
        </span>
        <h1 className="text-xl font-semibold capitalize">
          {title}
          {data && (
            <span className="ml-2 text-sm font-normal text-muted-foreground">
              {data.totalElements} items
            </span>
          )}
        </h1>
      </div>

      {/* Category switcher — change category without going back */}
      {allCategories.length > 0 && (
        <div className="-mx-1 flex flex-wrap gap-2 overflow-x-auto pb-1">
          {allCategories.map((c) => {
            const active = c.slug === slug;
            return (
              <Link
                key={c.id}
                href={ROUTES.category(c.slug)}
                className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-3 py-1.5 text-sm transition-colors ${
                  active
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'hover:border-primary hover:bg-accent'
                }`}
              >
                <span aria-hidden>{categoryEmoji(c.slug)}</span>
                {c.name}
              </Link>
            );
          })}
        </div>
      )}

      {isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 10 }).map((_, i) => (
            <Skeleton key={i} className="h-64 rounded-lg" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {data.content.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} className="pt-4" />
        </>
      ) : (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            Nothing in this category yet.
          </CardContent>
        </Card>
      )}
    </div>
  );
}
