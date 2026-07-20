'use client';

import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { useProductSearch } from '@/hooks';
import { ProductCard } from '@/components/product/product-card';
import { Card, CardContent, Skeleton, Pagination } from '@/components/ui';

function SearchResults() {
  const params = useSearchParams();
  const q = params.get('q') ?? '';
  const [page, setPage] = useState(0);
  // Reset to the first page whenever the query changes.
  useEffect(() => setPage(0), [q]);
  const { data, isLoading } = useProductSearch(q || undefined, page);

  return (
    <div className="container space-y-6 py-8">
      <h1 className="text-xl font-semibold">
        {q ? `Results for “${q}”` : 'All products'}
        {data && <span className="ml-2 text-sm text-muted-foreground">{data.totalElements} items</span>}
      </h1>

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
            No products match {q ? `“${q}”` : 'your search'}.
          </CardContent>
        </Card>
      )}
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={<div className="container py-8">Loading…</div>}>
      <SearchResults />
    </Suspense>
  );
}
