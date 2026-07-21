'use client';

import Link from 'next/link';
import { ROUTES, categoryEmoji } from '@/constants';
import { useCategories, useProductSearch } from '@/hooks';
import { ProductCard } from '@/components/product/product-card';
import { HeroCarousel } from '@/components/home/hero-carousel';
import { Testimonials } from '@/components/home/testimonials';
import { Card, CardContent, Skeleton } from '@/components/ui';

export default function HomePage() {
  const { data: categories, isLoading: catsLoading } = useCategories();
  const { data: products, isLoading: productsLoading } = useProductSearch();

  return (
    <div className="container space-y-10 py-8">
      {/* Hero carousel */}
      <HeroCarousel />

      {/* Categories */}
      <section>
        <h2 className="mb-4 text-xl font-semibold">Shop by category</h2>
        {catsLoading ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 md:grid-cols-7">
            {Array.from({ length: 7 }).map((_, i) => (
              <Skeleton key={i} className="h-24 rounded-lg" />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 md:grid-cols-7">
            {categories?.map((cat) => (
              <Link key={cat.id} href={ROUTES.category(cat.slug)}>
                <Card className="transition-colors hover:border-primary hover:bg-accent">
                  <CardContent className="flex h-24 flex-col items-center justify-center gap-2 p-3 text-center">
                    <span className="text-3xl" aria-hidden>
                      {categoryEmoji(cat.slug)}
                    </span>
                    <span className="text-xs font-medium">{cat.name}</span>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </section>

      {/* Products */}
      <section>
        <h2 className="mb-4 text-xl font-semibold">Fresh picks</h2>
        {productsLoading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 10 }).map((_, i) => (
              <Skeleton key={i} className="h-64 rounded-lg" />
            ))}
          </div>
        ) : products && products.content.length > 0 ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {products.content.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        ) : (
          <Card>
            <CardContent className="py-12 text-center text-muted-foreground">
              No products yet. Once the catalog is populated, fresh picks appear here.
            </CardContent>
          </Card>
        )}
      </section>

      {/* Testimonials */}
      <Testimonials />
    </div>
  );
}
