'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Plus, Search, Trash2, Leaf } from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency } from '@/lib';
import { useAdminProducts, useDeleteProduct, useUpdateProduct } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { PRODUCT_STATUSES } from '@/types';
import {
  Button,
  Card,
  CardContent,
  Input,
  Badge,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui';
import { AdminPageHeader, DataState } from '@/components/admin';

export default function AdminProductsPage() {
  const [term, setTerm] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const { data, isLoading, error, isFetching } = useAdminProducts(q, page);
  const del = useDeleteProduct();
  const update = useUpdateProduct();
  const { toast } = useToast();

  const onStatusChange = async (id: string, name: string, status: string) => {
    try {
      await update.mutateAsync({ id, payload: { status } });
      toast({ variant: 'success', title: 'Status updated', description: `${name} → ${status.toLowerCase()}` });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not change status.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setQ(term.trim());
  };

  const onDelete = async (id: string, name: string) => {
    if (!window.confirm(`Delete "${name}"? It will be hidden from the store.`)) return;
    try {
      await del.mutateAsync(id);
      toast({ variant: 'success', title: 'Product deleted', description: name });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not delete.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const rows = data?.content ?? [];

  return (
    <div>
      <AdminPageHeader
        title="Products"
        description={data ? `${data.totalElements} products` : 'Catalog products and variants.'}
        action={
          <Button asChild>
            <Link href={`${ROUTES.admin.products}/new`}>
              <Plus className="mr-1 h-4 w-4" /> New product
            </Link>
          </Button>
        }
      />

      <form onSubmit={onSearch} className="relative mb-4 max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={term}
          onChange={(e) => setTerm(e.target.value)}
          placeholder="Search products…"
          className="pl-9"
        />
      </form>

      <DataState
        isLoading={isLoading}
        error={error}
        isEmpty={rows.length === 0}
        emptyLabel={q ? `No products match "${q}".` : 'No products yet. Create the first one.'}
      />

      {rows.length > 0 && (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead className="text-right">Price</TableHead>
                  <TableHead className="text-right">Variants</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Link
                          href={`${ROUTES.admin.products}/${p.slug}`}
                          className="font-medium hover:text-primary hover:underline"
                        >
                          {p.name}
                        </Link>
                        {p.isOrganic && (
                          <Badge variant="success" className="gap-1">
                            <Leaf className="h-3 w-3" /> Organic
                          </Badge>
                        )}
                      </div>
                      <span className="text-xs text-muted-foreground">
                        /{p.slug}
                        {p.sku && ` · ${p.sku}`}
                      </span>
                    </TableCell>
                    <TableCell>
                      <select
                        value={p.status}
                        disabled={update.isPending}
                        onChange={(e) => onStatusChange(p.id, p.name, e.target.value)}
                        aria-label={`Status for ${p.name}`}
                        className="h-8 rounded-md border border-input bg-background px-2 text-xs capitalize"
                      >
                        {PRODUCT_STATUSES.map((s) => (
                          <option key={s} value={s}>
                            {s.replace(/_/g, ' ').toLowerCase()}
                          </option>
                        ))}
                      </select>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{p.categoryName ?? '—'}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCurrency(p.price)}
                      {p.mrp != null && p.mrp !== p.price && (
                        <span className="ml-1 text-xs text-muted-foreground line-through">
                          {formatCurrency(p.mrp)}
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{p.variantCount}</TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => onDelete(p.id, p.name)}
                        aria-label={`Delete ${p.name}`}
                      >
                        <Trash2 className="h-4 w-4 text-muted-foreground" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <span className="text-sm text-muted-foreground">
            Page {page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={data.first || isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={data.last || isFetching}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
