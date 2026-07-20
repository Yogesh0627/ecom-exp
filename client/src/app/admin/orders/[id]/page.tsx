'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { ROUTES } from '@/constants';
import { dayjs, formatCurrency } from '@/lib';
import { useAdminOrder, useTransitionOrder } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { ORDER_TRANSITIONS, type OrderStatus } from '@/types';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Input,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui';
import { AdminPageHeader, DataState, ORDER_STATUS_TONE, statusLabel } from '@/components/admin';
import { InvoiceButton } from '@/components/order/invoice-button';

const INVOICEABLE: OrderStatus[] = [
  'PAID',
  'CONFIRMED',
  'PACKED',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'RETURNED',
  'REFUNDED',
];

export default function AdminOrderDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { data: order, isLoading, error } = useAdminOrder(id);
  const transition = useTransitionOrder();
  const { toast } = useToast();
  const [note, setNote] = useState('');

  const nextStates: OrderStatus[] = order ? ORDER_TRANSITIONS[order.status] : [];

  const onTransition = async (status: OrderStatus) => {
    try {
      await transition.mutateAsync({ id, status, note: note.trim() || undefined });
      toast({ variant: 'success', title: 'Order updated', description: `→ ${statusLabel(status)}` });
      setNote('');
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not update the order.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <Link
        href={ROUTES.admin.orders}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> Back to orders
      </Link>

      <DataState isLoading={isLoading} error={error} skeletonRows={6} />

      {order && (
        <>
          <AdminPageHeader
            title={order.orderNumber}
            description={order.placedAt ? `Placed ${dayjs(order.placedAt).format('LLL')}` : undefined}
            action={
              <>
                {INVOICEABLE.includes(order.status) && <InvoiceButton orderId={order.id} />}
                <Badge variant={ORDER_STATUS_TONE[order.status]} className="text-sm capitalize">
                  {statusLabel(order.status)}
                </Badge>
              </>
            }
          />

          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              {/* Items */}
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Items</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Product</TableHead>
                        <TableHead className="text-right">Qty</TableHead>
                        <TableHead className="text-right">Unit</TableHead>
                        <TableHead className="text-right">Line total</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {order.items.map((item) => (
                        <TableRow key={item.id}>
                          <TableCell>
                            <span className="font-medium">{item.productName}</span>
                            <span className="block text-xs text-muted-foreground">
                              {item.variantName} · {item.sku}
                            </span>
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{item.qty}</TableCell>
                          <TableCell className="text-right tabular-nums">
                            {formatCurrency(item.unitPrice)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {formatCurrency(item.lineTotal)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>

              {/* Totals */}
              <Card>
                <CardContent className="space-y-1.5 p-5 text-sm">
                  <Row label="Subtotal" value={formatCurrency(order.subtotal)} />
                  {order.discountTotal > 0 && (
                    <Row label="Discount" value={`− ${formatCurrency(order.discountTotal)}`} />
                  )}
                  <Row label="Tax" value={formatCurrency(order.taxTotal)} />
                  <Row label="Shipping" value={formatCurrency(order.shippingFee)} />
                  <div className="mt-2 border-t pt-2">
                    <Row label="Grand total" value={formatCurrency(order.grandTotal)} bold />
                  </div>
                </CardContent>
              </Card>

              {/* History */}
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Status history</CardTitle>
                </CardHeader>
                <CardContent>
                  <ol className="space-y-3">
                    {order.history.map((h, i) => (
                      <li key={i} className="flex items-start gap-3 text-sm">
                        <div className="mt-1 h-2 w-2 shrink-0 rounded-full bg-primary" />
                        <div>
                          <p className="capitalize">
                            {h.fromStatus ? `${statusLabel(h.fromStatus)} → ` : ''}
                            <span className="font-medium">{statusLabel(h.toStatus)}</span>
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {dayjs(h.at).format('lll')}
                            {h.note && ` · ${h.note}`}
                          </p>
                        </div>
                      </li>
                    ))}
                  </ol>
                </CardContent>
              </Card>
            </div>

            {/* Ship-to + actions */}
            <div className="space-y-6">
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Ship to</CardTitle>
                </CardHeader>
                <CardContent className="text-sm">
                  <p className="font-medium">{order.shipTo.recipientName}</p>
                  <p className="text-muted-foreground">{order.shipTo.phone}</p>
                  <p className="mt-2">
                    {order.shipTo.line1}
                    {order.shipTo.line2 && `, ${order.shipTo.line2}`}
                  </p>
                  <p>
                    {order.shipTo.city}, {order.shipTo.state} {order.shipTo.pincode}
                  </p>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Advance status</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  {nextStates.length === 0 ? (
                    <p className="text-sm text-muted-foreground">
                      This order is in a terminal state — no further transitions.
                    </p>
                  ) : (
                    <>
                      <Input
                        value={note}
                        onChange={(e) => setNote(e.target.value)}
                        placeholder="Note (optional)"
                      />
                      <div className="flex flex-col gap-2">
                        {nextStates.map((s) => (
                          <Button
                            key={s}
                            variant={s === 'CANCELLED' || s === 'REFUNDED' ? 'outline' : 'default'}
                            disabled={transition.isPending}
                            onClick={() => onTransition(s)}
                            className="justify-between capitalize"
                          >
                            {statusLabel(s)}
                            <ArrowRight className="h-4 w-4" />
                          </Button>
                        ))}
                      </div>
                    </>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className="flex justify-between">
      <span className={bold ? 'font-semibold' : 'text-muted-foreground'}>{label}</span>
      <span className={bold ? 'font-semibold tabular-nums' : 'tabular-nums'}>{value}</span>
    </div>
  );
}
