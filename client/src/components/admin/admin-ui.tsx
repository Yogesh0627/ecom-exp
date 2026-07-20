'use client';

import type { LucideIcon } from 'lucide-react';
import { AlertTriangle, Inbox } from 'lucide-react';
import { cn } from '@/lib';
import { Card, CardContent, Skeleton } from '@/components/ui';

type Tone = 'default' | 'secondary' | 'success' | 'warning' | 'destructive';

/** Humanise an enum-ish status: PENDING_PAYMENT -> "pending payment". */
export function statusLabel(s: string): string {
  return s.replace(/_/g, ' ').toLowerCase();
}

/** Order status -> badge tone, shared across the orders list and detail views. */
export const ORDER_STATUS_TONE: Record<string, Tone> = {
  PENDING_PAYMENT: 'warning',
  PAID: 'success',
  CONFIRMED: 'default',
  PACKED: 'default',
  SHIPPED: 'default',
  OUT_FOR_DELIVERY: 'default',
  DELIVERED: 'success',
  CANCELLED: 'destructive',
  RETURNED: 'warning',
  REFUNDED: 'destructive',
};

/** Consistent page title + optional action slot for every admin section. */
export function AdminPageHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      </div>
      {action && <div className="flex items-center gap-2">{action}</div>}
    </div>
  );
}

/** A single KPI tile. `tone` colours the value for at-a-glance health (e.g. drift should be 0). */
export function StatCard({
  label,
  value,
  icon: Icon,
  hint,
  tone = 'default',
}: {
  label: string;
  value: React.ReactNode;
  icon?: LucideIcon;
  hint?: string;
  tone?: 'default' | 'success' | 'warning' | 'destructive';
}) {
  const toneClass = {
    default: 'text-foreground',
    success: 'text-success',
    warning: 'text-warning',
    destructive: 'text-destructive',
  }[tone];

  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">{label}</p>
          {Icon && <Icon className="h-4 w-4 text-muted-foreground" />}
        </div>
        <p className={cn('mt-2 text-2xl font-bold tabular-nums', toneClass)}>{value}</p>
        {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  );
}

/**
 * One place to render the loading / error / empty states of a data view, so every admin table
 * behaves the same. Returns null when there's data to show (the caller renders it).
 */
export function DataState({
  isLoading,
  error,
  isEmpty,
  emptyLabel = 'Nothing here yet.',
  skeletonRows = 5,
}: {
  isLoading: boolean;
  error?: unknown;
  isEmpty?: boolean;
  emptyLabel?: string;
  skeletonRows?: number;
}) {
  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: skeletonRows }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full rounded-md" />
        ))}
      </div>
    );
  }
  if (error) {
    const status = (error as { response?: { status?: number } })?.response?.status;
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-12 text-center">
          <AlertTriangle className="h-8 w-8 text-destructive" />
          <p className="font-medium">
            {status === 403 ? "You don't have permission to view this." : 'Could not load this data.'}
          </p>
          {status !== 403 && (
            <p className="text-sm text-muted-foreground">Please refresh and try again.</p>
          )}
        </CardContent>
      </Card>
    );
  }
  if (isEmpty) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
          <Inbox className="h-8 w-8 opacity-40" />
          <p>{emptyLabel}</p>
        </CardContent>
      </Card>
    );
  }
  return null;
}
