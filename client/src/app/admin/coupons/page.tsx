'use client';

import { useState } from 'react';
import { Plus, Ticket } from 'lucide-react';
import { dayjs, formatCurrency } from '@/lib';
import { useCoupons, useCreateCoupon } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { COUPON_TYPES, type CouponType } from '@/types';
import {
  Button,
  Card,
  CardContent,
  Input,
  Label,
  Badge,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogTrigger,
} from '@/components/ui';
import { AdminPageHeader, DataState, statusLabel } from '@/components/admin';

const TYPE_LABEL: Record<CouponType, string> = {
  PERCENT: '% off',
  FLAT: '₹ off',
  FREE_SHIPPING: 'Free shipping',
};

function couponValueLabel(type: CouponType, value: number): string {
  if (type === 'PERCENT') return `${value}%`;
  if (type === 'FLAT') return formatCurrency(value);
  return '—';
}

const emptyForm = {
  code: '',
  description: '',
  type: 'PERCENT' as CouponType,
  value: '',
  maxDiscount: '',
  minCartValue: '',
  validFrom: '',
  validUntil: '',
  maxUses: '',
  maxUsesPerUser: '1',
  firstOrderOnly: false,
};

export default function AdminCouponsPage() {
  const { data: coupons, isLoading, error } = useCoupons();
  const create = useCreateCoupon();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const set = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }));

  const valid =
    form.code.trim() &&
    form.validFrom &&
    form.validUntil &&
    (form.type === 'FREE_SHIPPING' || Number(form.value) > 0);

  const onCreate = async () => {
    try {
      await create.mutateAsync({
        code: form.code.trim().toUpperCase(),
        description: form.description.trim() || undefined,
        type: form.type,
        value: form.type === 'FREE_SHIPPING' ? 0 : Number(form.value),
        maxDiscount: form.maxDiscount ? Number(form.maxDiscount) : undefined,
        minCartValue: form.minCartValue ? Number(form.minCartValue) : undefined,
        // Send full-day ISO instants for the chosen dates.
        validFrom: new Date(`${form.validFrom}T00:00:00`).toISOString(),
        validUntil: new Date(`${form.validUntil}T23:59:59`).toISOString(),
        maxUses: form.maxUses ? Number(form.maxUses) : undefined,
        maxUsesPerUser: form.maxUsesPerUser ? Number(form.maxUsesPerUser) : undefined,
        firstOrderOnly: form.firstOrderOnly,
      });
      toast({ variant: 'success', title: 'Coupon created', description: form.code.toUpperCase() });
      setOpen(false);
      setForm(emptyForm);
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not create the coupon.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const rows = coupons ?? [];

  return (
    <div>
      <AdminPageHeader
        title="Coupons"
        description="Discount codes customers can apply at checkout."
        action={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="mr-1 h-4 w-4" /> New coupon
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>New coupon</DialogTitle>
              </DialogHeader>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label className="text-xs">Code</Label>
                  <Input
                    value={form.code}
                    onChange={(e) => set({ code: e.target.value.toUpperCase() })}
                    placeholder="WELCOME10"
                    className="mt-1 font-mono"
                  />
                </div>
                <div>
                  <Label className="text-xs">Type</Label>
                  <select
                    value={form.type}
                    onChange={(e) => set({ type: e.target.value as CouponType })}
                    className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    {COUPON_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {TYPE_LABEL[t]}
                      </option>
                    ))}
                  </select>
                </div>
                {form.type !== 'FREE_SHIPPING' && (
                  <>
                    <div>
                      <Label className="text-xs">
                        {form.type === 'PERCENT' ? 'Percent off' : 'Amount off (₹)'}
                      </Label>
                      <Input
                        type="number"
                        min="0"
                        value={form.value}
                        onChange={(e) => set({ value: e.target.value })}
                        className="mt-1"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Max discount (₹, optional)</Label>
                      <Input
                        type="number"
                        min="0"
                        value={form.maxDiscount}
                        onChange={(e) => set({ maxDiscount: e.target.value })}
                        className="mt-1"
                      />
                    </div>
                  </>
                )}
                <div>
                  <Label className="text-xs">Min cart value (₹)</Label>
                  <Input
                    type="number"
                    min="0"
                    value={form.minCartValue}
                    onChange={(e) => set({ minCartValue: e.target.value })}
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label className="text-xs">Max uses per user</Label>
                  <Input
                    type="number"
                    min="1"
                    value={form.maxUsesPerUser}
                    onChange={(e) => set({ maxUsesPerUser: e.target.value })}
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label className="text-xs">Valid from</Label>
                  <Input
                    type="date"
                    value={form.validFrom}
                    onChange={(e) => set({ validFrom: e.target.value })}
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label className="text-xs">Valid until</Label>
                  <Input
                    type="date"
                    value={form.validUntil}
                    onChange={(e) => set({ validUntil: e.target.value })}
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label className="text-xs">Total uses cap (optional)</Label>
                  <Input
                    type="number"
                    min="1"
                    value={form.maxUses}
                    onChange={(e) => set({ maxUses: e.target.value })}
                    className="mt-1"
                  />
                </div>
                <label className="col-span-2 flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.firstOrderOnly}
                    onChange={(e) => set({ firstOrderOnly: e.target.checked })}
                    className="h-4 w-4"
                  />
                  First order only
                </label>
                <div className="col-span-2">
                  <Label className="text-xs">Description (optional)</Label>
                  <Input
                    value={form.description}
                    onChange={(e) => set({ description: e.target.value })}
                    className="mt-1"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setOpen(false)}>
                  Cancel
                </Button>
                <Button onClick={onCreate} disabled={!valid || create.isPending}>
                  {create.isPending ? 'Creating…' : 'Create'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        }
      />

      <DataState
        isLoading={isLoading}
        error={error}
        isEmpty={rows.length === 0}
        emptyLabel="No coupons yet. Create one to start offering discounts."
      />

      {rows.length > 0 && (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead className="text-right">Value</TableHead>
                  <TableHead className="text-right">Used</TableHead>
                  <TableHead>Valid until</TableHead>
                  <TableHead>State</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((c) => {
                  const expired = dayjs(c.validUntil).isBefore(dayjs());
                  return (
                    <TableRow key={c.id}>
                      <TableCell>
                        <span className="font-mono font-medium">{c.code}</span>
                        {c.firstOrderOnly && (
                          <Badge variant="secondary" className="ml-2">
                            1st order
                          </Badge>
                        )}
                        {c.description && (
                          <span className="block text-xs text-muted-foreground">{c.description}</span>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {statusLabel(c.type)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {couponValueLabel(c.type, c.value)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {c.timesUsed}
                        {c.maxUses != null && ` / ${c.maxUses}`}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {dayjs(c.validUntil).format('ll')}
                      </TableCell>
                      <TableCell>
                        {!c.isActive ? (
                          <Badge variant="secondary">inactive</Badge>
                        ) : expired ? (
                          <Badge variant="warning">expired</Badge>
                        ) : (
                          <Badge variant="success">active</Badge>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
