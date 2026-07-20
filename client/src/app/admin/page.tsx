'use client';

import {
  IndianRupee,
  ShoppingCart,
  Package,
  Users,
  AlertTriangle,
  Star,
  ClipboardCheck,
  ShieldCheck,
} from 'lucide-react';
import { useDashboardSummary, useTopProducts } from '@/hooks';
import { formatCurrency, formatNumber } from '@/lib';
import {
  Badge,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui';
import { AdminPageHeader, StatCard, DataState } from '@/components/admin';

/** Order status → badge tone, so the status breakdown reads at a glance. */
const STATUS_TONE: Record<string, 'default' | 'secondary' | 'success' | 'warning' | 'destructive'> = {
  DELIVERED: 'success',
  SHIPPED: 'default',
  OUT_FOR_DELIVERY: 'default',
  PACKED: 'default',
  CONFIRMED: 'default',
  PAID: 'success',
  PENDING_PAYMENT: 'warning',
  CANCELLED: 'destructive',
  REFUNDED: 'destructive',
};

export default function AdminDashboardPage() {
  const { data, isLoading, error } = useDashboardSummary();
  const { data: top, isLoading: topLoading } = useTopProducts(10);

  return (
    <div>
      <AdminPageHeader
        title="Dashboard"
        description="Revenue, orders, and everything that needs attention right now."
      />

      <DataState isLoading={isLoading} error={error} />

      {data && (
        <div className="space-y-8">
          {/* Revenue + volume */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Revenue (paid)"
              value={formatCurrency(data.revenue)}
              icon={IndianRupee}
              hint={`${formatNumber(data.paidOrders)} paid orders`}
            />
            <StatCard
              label="Today's revenue"
              value={formatCurrency(data.todayRevenue)}
              icon={IndianRupee}
              hint={`${formatNumber(data.todayOrders)} orders today`}
            />
            <StatCard
              label="Active products"
              value={formatNumber(data.activeProducts)}
              icon={Package}
            />
            <StatCard label="Customers" value={formatNumber(data.totalCustomers)} icon={Users} />
          </div>

          {/* Needs attention */}
          <div>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
              Needs attention
            </h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <StatCard
                label="Low-stock alerts"
                value={formatNumber(data.lowStockAlerts)}
                icon={AlertTriangle}
                tone={data.lowStockAlerts > 0 ? 'warning' : 'default'}
              />
              <StatCard
                label="Reviews to moderate"
                value={formatNumber(data.pendingReviews)}
                icon={Star}
                tone={data.pendingReviews > 0 ? 'warning' : 'default'}
              />
              <StatCard
                label="Stock adjustments to approve"
                value={formatNumber(data.pendingAdjustments)}
                icon={ClipboardCheck}
                tone={data.pendingAdjustments > 0 ? 'warning' : 'default'}
              />
              <StatCard
                label="Payment mismatches"
                value={formatNumber(data.paymentMismatches)}
                icon={ShieldCheck}
                tone={data.paymentMismatches > 0 ? 'destructive' : 'success'}
                hint="Captured money vs order total"
              />
              <StatCard
                label="Inventory ledger drift"
                value={formatNumber(data.ledgerDrift)}
                icon={ShieldCheck}
                tone={data.ledgerDrift > 0 ? 'destructive' : 'success'}
                hint="Should always be zero"
              />
            </div>
          </div>

          {/* Orders by status */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Orders by status</CardTitle>
            </CardHeader>
            <CardContent>
              {Object.keys(data.ordersByStatus).length === 0 ? (
                <p className="text-sm text-muted-foreground">No orders yet.</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {Object.entries(data.ordersByStatus).map(([status, count]) => (
                    <Badge key={status} variant={STATUS_TONE[status] ?? 'secondary'}>
                      {status.replace(/_/g, ' ').toLowerCase()}: {formatNumber(count)}
                    </Badge>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Top products */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Top sellers</CardTitle>
            </CardHeader>
            <CardContent>
              <DataState
                isLoading={topLoading}
                isEmpty={!top || top.length === 0}
                emptyLabel="No shipped orders yet — top sellers appear once orders ship."
                skeletonRows={4}
              />
              {top && top.length > 0 && (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Product</TableHead>
                      <TableHead className="text-right">Units</TableHead>
                      <TableHead className="text-right">Revenue</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {top.map((row) => (
                      <TableRow key={row.product}>
                        <TableCell className="font-medium">{row.product}</TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatNumber(row.units)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCurrency(row.revenue)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
