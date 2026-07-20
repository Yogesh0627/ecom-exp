'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Package, X } from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency, dayjs } from '@/lib';
import { useMyOrders, useCancelOrder, useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { Button, Card, CardContent, Badge, Skeleton, Pagination, Breadcrumbs } from '@/components/ui';
import type { OrderBucket } from '@/api';

const TABS: { key: OrderBucket; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'active', label: 'In progress' },
  { key: 'delivered', label: 'Delivered' },
  { key: 'cancelled', label: 'Cancelled' },
];

// Cancellable while nothing has shipped (mirrors the backend state machine).
const CANCELLABLE = new Set(['PENDING_PAYMENT', 'PAID', 'CONFIRMED', 'PACKED']);

function statusTone(status: string): 'success' | 'warning' | 'destructive' | 'secondary' {
  if (status === 'DELIVERED') return 'success';
  if (status === 'CANCELLED' || status === 'RETURNED' || status === 'REFUNDED') return 'destructive';
  if (status === 'PENDING_PAYMENT') return 'warning';
  return 'secondary';
}

export default function OrdersPage() {
  const { isAuthenticated, isReady } = useAuth();
  const [bucket, setBucket] = useState<OrderBucket>('all');
  const [page, setPage] = useState(0);
  const { data, isLoading } = useMyOrders(bucket, page);
  const cancel = useCancelOrder();
  const { toast } = useToast();

  if (isReady && !isAuthenticated) {
    return (
      <div className="container py-20 text-center">
        <p className="mb-4 text-muted-foreground">Sign in to see your orders.</p>
        <Button asChild>
          <Link href={ROUTES.login}>Sign in</Link>
        </Button>
      </div>
    );
  }

  const switchTab = (b: OrderBucket) => {
    setBucket(b);
    setPage(0);
  };

  const onCancel = (id: string) => {
    if (!window.confirm('Cancel this order? This cannot be undone.')) return;
    cancel.mutate(
      { id, reason: 'Cancelled by customer' },
      {
        onSuccess: () => toast({ variant: 'success', title: 'Order cancelled' }),
        onError: (e: unknown) =>
          toast({
            variant: 'destructive',
            title: 'Could not cancel',
            description:
              (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
              'This order can no longer be cancelled.',
          }),
      },
    );
  };

  const orders = data?.content ?? [];

  return (
    <div className="container max-w-3xl space-y-4 py-8">
      <Breadcrumbs items={[{ label: 'My account', href: ROUTES.account }, { label: 'Orders' }]} />
      <h1 className="text-xl font-semibold">Your orders</h1>

      {/* Tabs */}
      <div className="flex flex-wrap gap-1 border-b">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => switchTab(t.key)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
              bucket === t.key
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-20 rounded-lg" />)
      ) : orders.length > 0 ? (
        <>
          {orders.map((o) => (
            <Card key={o.id} className="transition-shadow hover:shadow-md">
              <CardContent className="flex flex-wrap items-center gap-4 p-4">
                <Package className="h-8 w-8 text-muted-foreground" />
                <Link href={ROUTES.order(o.id)} className="min-w-0 flex-1">
                  <p className="font-medium hover:underline">{o.orderNumber}</p>
                  <p className="text-xs text-muted-foreground">
                    {o.itemCount} item(s) · {o.placedAt ? dayjs(o.placedAt).format('LL') : ''}
                  </p>
                </Link>
                <Badge variant={statusTone(o.status)}>{o.status.replace(/_/g, ' ')}</Badge>
                <span className="font-semibold">{formatCurrency(o.grandTotal)}</span>
                {CANCELLABLE.has(o.status) && (
                  <Button
                    size="sm"
                    variant="outline"
                    className="text-destructive"
                    onClick={() => onCancel(o.id)}
                    disabled={cancel.isPending}
                  >
                    <X className="mr-1 h-3 w-3" /> Cancel
                  </Button>
                )}
              </CardContent>
            </Card>
          ))}

          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            onPageChange={setPage}
            className="pt-2"
          />
        </>
      ) : (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            {bucket === 'all' ? (
              <>
                No orders yet.{' '}
                <Link href={ROUTES.home} className="text-primary hover:underline">
                  Start shopping
                </Link>
                .
              </>
            ) : (
              'No orders in this category.'
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
