'use client';

import Link from 'next/link';
import { useState } from 'react';
import { ROUTES } from '@/constants';
import { dayjs, formatCurrency } from '@/lib';
import { useAdminOrders } from '@/hooks';
import type { OrderStatus } from '@/types';
import {
  Button,
  Card,
  CardContent,
  Badge,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui';
import { AdminPageHeader, DataState, ORDER_STATUS_TONE, statusLabel } from '@/components/admin';

const STATUSES: OrderStatus[] = [
  'PENDING_PAYMENT',
  'PAID',
  'CONFIRMED',
  'PACKED',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'RETURNED',
  'REFUNDED',
];

export default function AdminOrdersPage() {
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);
  const { data, isLoading, error, isFetching } = useAdminOrders(status, page);

  const rows = data?.content ?? [];

  return (
    <div>
      <AdminPageHeader
        title="Orders"
        description={data ? `${data.totalElements} orders` : 'All customer orders.'}
      />

      <div className="mb-4 flex flex-wrap gap-2">
        <button
          onClick={() => {
            setStatus('');
            setPage(0);
          }}
          className={`rounded-md border px-3 py-1.5 text-sm ${status === '' ? 'border-primary bg-primary text-primary-foreground' : 'hover:border-primary'}`}
        >
          All
        </button>
        {STATUSES.map((s) => (
          <button
            key={s}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
            className={`rounded-md border px-3 py-1.5 text-sm capitalize ${status === s ? 'border-primary bg-primary text-primary-foreground' : 'hover:border-primary'}`}
          >
            {statusLabel(s)}
          </button>
        ))}
      </div>

      <DataState
        isLoading={isLoading}
        error={error}
        isEmpty={rows.length === 0}
        emptyLabel={status ? `No ${statusLabel(status)} orders.` : 'No orders yet.'}
      />

      {rows.length > 0 && (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Order</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Items</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                  <TableHead>Placed</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell className="font-mono text-sm font-medium">{o.orderNumber}</TableCell>
                    <TableCell>
                      <Badge variant={ORDER_STATUS_TONE[o.status]} className="capitalize">
                        {statusLabel(o.status)}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{o.itemCount}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCurrency(o.grandTotal)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {o.placedAt ? dayjs(o.placedAt).format('lll') : '—'}
                    </TableCell>
                    <TableCell>
                      <Button asChild variant="ghost" size="sm">
                        <Link href={ROUTES.admin.order(o.id)}>View</Link>
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
            <Button variant="outline" size="sm" disabled={data.first || isFetching} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <Button variant="outline" size="sm" disabled={data.last || isFetching} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
