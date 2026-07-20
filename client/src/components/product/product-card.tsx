import Link from 'next/link';
import { Leaf } from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency } from '@/lib';
import { Card, CardContent, Badge } from '@/components/ui';
import type { ProductSummary } from '@/types';

/** A storefront product tile. Shows price, MRP strike-through, and the derived discount. */
export function ProductCard({ product }: { product: ProductSummary }) {
  const hasDiscount = product.discountPercent > 0;
  return (
    <Link href={ROUTES.product(product.slug)} className="group block">
      <Card className="h-full overflow-hidden transition-shadow hover:shadow-md">
        <div className="relative aspect-square bg-muted">
          {product.imageUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={product.imageUrl}
              alt={product.name}
              className="h-full w-full object-cover transition-transform group-hover:scale-105"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-muted-foreground">
              <Leaf className="h-10 w-10 opacity-30" />
            </div>
          )}
          {product.isOrganic && (
            <Badge variant="success" className="absolute left-2 top-2 gap-1">
              <Leaf className="h-3 w-3" /> Organic
            </Badge>
          )}
          {hasDiscount && (
            <Badge variant="destructive" className="absolute right-2 top-2">
              {Math.round(product.discountPercent)}% off
            </Badge>
          )}
        </div>
        <CardContent className="p-3">
          <p className="line-clamp-2 min-h-10 text-sm font-medium">{product.name}</p>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="font-semibold">{formatCurrency(product.price)}</span>
            {hasDiscount && (
              <span className="text-xs text-muted-foreground line-through">
                {formatCurrency(product.mrp)}
              </span>
            )}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
