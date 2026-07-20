'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { CheckCircle2, Package } from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency, dayjs } from '@/lib';
import { useOrder } from '@/hooks';
import { Button, Card, CardContent, CardHeader, CardTitle, Badge, Separator, Skeleton, Breadcrumbs } from '@/components/ui';
import { InvoiceButton } from '@/components/order/invoice-button';
import { PayNowButton } from '@/components/order/pay-now-button';
import { TestPaymentNote } from '@/components/order/test-payment-note';

const INVOICEABLE = new Set([
  'PAID',
  'CONFIRMED',
  'PACKED',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'RETURNED',
  'REFUNDED',
]);

const STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: 'Awaiting payment',
  PAID: 'Paid',
  CONFIRMED: 'Confirmed',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  RETURNED: 'Returned',
  REFUNDED: 'Refunded',
};

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: order, isLoading } = useOrder(id);

  if (isLoading) return <div className="container py-8"><Skeleton className="h-96 rounded-lg" /></div>;
  if (!order) return <div className="container py-20 text-center text-muted-foreground">Order not found.</div>;

  const isIntraState = order.igstTotal === 0;

  return (
    <div className="container max-w-3xl space-y-6 py-8">
      <Breadcrumbs
        items={[{ label: 'Orders', href: ROUTES.orders }, { label: order.orderNumber }]}
      />
      <div className="rounded-xl border bg-accent/50 p-6 text-center">
        <CheckCircle2 className="mx-auto mb-2 h-10 w-10 text-primary" />
        <h1 className="text-xl font-bold">Order {order.orderNumber}</h1>
        <p className="text-sm text-muted-foreground">
          Placed {order.placedAt ? dayjs(order.placedAt).format('LLL') : ''}
        </p>
        <div className="mt-3 flex items-center justify-center gap-3">
          <Badge>{STATUS_LABEL[order.status] ?? order.status}</Badge>
          {order.status === 'PENDING_PAYMENT' && <PayNowButton orderId={order.id} />}
          {INVOICEABLE.has(order.status) && <InvoiceButton orderId={order.id} />}
        </div>
        {order.status === 'PENDING_PAYMENT' && (
          <TestPaymentNote className="mx-auto mt-4 max-w-md text-left" />
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Package className="h-5 w-5" /> Items
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {order.items.map((i) => (
            <div key={i.id} className="flex justify-between text-sm">
              <span>
                {/* Rendered from the snapshot — this is what was bought, immune to later catalog edits */}
                {i.productName} <span className="text-muted-foreground">× {i.qty}</span>
                {i.taxRatePct > 0 && (
                  <span className="ml-2 text-xs text-muted-foreground">GST {i.taxRatePct}%</span>
                )}
              </span>
              <span className="font-medium">{formatCurrency(i.lineTotal)}</span>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Payment summary</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <Row label="Subtotal" value={formatCurrency(order.subtotal)} />
          {order.discountTotal > 0 && (
            <Row label="Discount" value={`− ${formatCurrency(order.discountTotal)}`} />
          )}
          {isIntraState ? (
            <>
              <Row label="CGST" value={formatCurrency(order.cgstTotal)} />
              <Row label="SGST" value={formatCurrency(order.sgstTotal)} />
            </>
          ) : (
            <Row label="IGST" value={formatCurrency(order.igstTotal)} />
          )}
          <Row label="Delivery" value={order.shippingFee === 0 ? 'FREE' : formatCurrency(order.shippingFee)} />
          <Separator />
          <div className="flex justify-between text-base font-semibold">
            <span>Total</span>
            <span>{formatCurrency(order.grandTotal)}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Delivering to</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          <p className="font-medium text-foreground">{order.shipTo.recipientName}</p>
          <p>{order.shipTo.phone}</p>
          <p>
            {order.shipTo.line1}, {order.shipTo.city}, {order.shipTo.state} {order.shipTo.pincode}
          </p>
        </CardContent>
      </Card>

      <div className="text-center">
        <Button asChild variant="outline">
          <Link href={ROUTES.orders}>View all orders</Link>
        </Button>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span>{value}</span>
    </div>
  );
}
