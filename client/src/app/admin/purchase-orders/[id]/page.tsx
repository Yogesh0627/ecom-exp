'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { ArrowLeft, Send, PackageCheck, XCircle } from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency } from '@/lib';
import {
  usePurchaseOrder,
  useSubmitPurchaseOrder,
  useReceivePurchaseOrder,
  useCancelPurchaseOrder,
} from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { PoStatus, ReceivePoLine } from '@/types';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui';
import { AdminPageHeader, DataState, statusLabel } from '@/components/admin';

const PO_STATUS_TONE: Record<PoStatus, 'default' | 'secondary' | 'success' | 'warning' | 'destructive'> = {
  DRAFT: 'secondary',
  SUBMITTED: 'default',
  PARTIALLY_RECEIVED: 'warning',
  RECEIVED: 'success',
  CANCELLED: 'destructive',
};

interface ReceiptDraft {
  qty: string;
  lotNo: string;
  expiryDate: string;
}

export default function AdminPurchaseOrderDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { data: po, isLoading, error } = usePurchaseOrder(id);
  const submit = useSubmitPurchaseOrder();
  const receive = useReceivePurchaseOrder();
  const cancel = useCancelPurchaseOrder();
  const { toast } = useToast();

  const [receipts, setReceipts] = useState<Record<string, ReceiptDraft>>({});

  const setReceipt = (poItemId: string, patch: Partial<ReceiptDraft>) =>
    setReceipts((r) => ({
      ...r,
      [poItemId]: { ...(r[poItemId] ?? { qty: '', lotNo: '', expiryDate: '' }), ...patch },
    }));

  const err = (e: unknown) =>
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Action failed.';

  const canReceive = po?.status === 'SUBMITTED' || po?.status === 'PARTIALLY_RECEIVED';
  const isOpen = po && po.status !== 'RECEIVED' && po.status !== 'CANCELLED';

  const onSubmit = async () => {
    try {
      await submit.mutateAsync(id);
      toast({ variant: 'success', title: 'PO submitted to supplier' });
    } catch (e) {
      toast({ variant: 'destructive', title: 'Failed', description: err(e) });
    }
  };

  const onCancel = async () => {
    if (!window.confirm('Cancel this purchase order?')) return;
    try {
      await cancel.mutateAsync({ id });
      toast({ variant: 'success', title: 'PO cancelled' });
    } catch (e) {
      toast({ variant: 'destructive', title: 'Failed', description: err(e) });
    }
  };

  const onReceive = async () => {
    const lines: ReceivePoLine[] = Object.entries(receipts)
      .filter(([, r]) => Number(r.qty) > 0)
      .map(([poItemId, r]) => ({
        poItemId,
        qtyReceived: Number(r.qty),
        lotNo: r.lotNo.trim() || undefined,
        expiryDate: r.expiryDate || undefined,
      }));
    if (lines.length === 0) {
      toast({ variant: 'destructive', title: 'Nothing to receive', description: 'Enter a quantity on at least one line.' });
      return;
    }
    try {
      await receive.mutateAsync({ id, receipts: lines });
      toast({ variant: 'success', title: 'Stock received', description: `${lines.length} line(s)` });
      setReceipts({});
    } catch (e) {
      toast({ variant: 'destructive', title: 'Failed', description: err(e) });
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href={ROUTES.admin.purchaseOrders}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> Back to purchase orders
      </Link>

      <DataState isLoading={isLoading} error={error} skeletonRows={5} />

      {po && (
        <>
          <AdminPageHeader
            title={po.poNumber}
            action={
              <Badge variant={PO_STATUS_TONE[po.status]} className="text-sm capitalize">
                {statusLabel(po.status)}
              </Badge>
            }
          />

          <Card className="mb-6">
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Lines</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>SKU</TableHead>
                    <TableHead className="text-right">Ordered</TableHead>
                    <TableHead className="text-right">Received</TableHead>
                    <TableHead className="text-right">Outstanding</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {po.lines.map((l) => (
                    <TableRow key={l.poItemId}>
                      <TableCell className="font-mono text-xs">{l.sku}</TableCell>
                      <TableCell className="text-right tabular-nums">{l.qtyOrdered}</TableCell>
                      <TableCell className="text-right tabular-nums">{l.qtyReceived}</TableCell>
                      <TableCell className="text-right tabular-nums font-medium">{l.outstanding}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="flex justify-end border-t px-4 py-3 text-sm">
                <span className="text-muted-foreground">Total:&nbsp;</span>
                <span className="font-semibold tabular-nums">{formatCurrency(po.grandTotal)}</span>
              </div>
            </CardContent>
          </Card>

          {/* Actions */}
          {isOpen && (
            <div className="space-y-6">
              <div className="flex gap-2">
                {po.status === 'DRAFT' && (
                  <Button onClick={onSubmit} disabled={submit.isPending}>
                    <Send className="mr-1 h-4 w-4" /> Submit to supplier
                  </Button>
                )}
                <Button variant="outline" onClick={onCancel} disabled={cancel.isPending}>
                  <XCircle className="mr-1 h-4 w-4" /> Cancel PO
                </Button>
              </div>

              {canReceive && (
                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">Receive stock</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    {po.lines
                      .filter((l) => l.outstanding > 0)
                      .map((l) => (
                        <div key={l.poItemId} className="grid grid-cols-1 gap-2 sm:grid-cols-4 sm:items-end">
                          <div className="sm:col-span-1">
                            <span className="font-mono text-xs">{l.sku}</span>
                            <span className="block text-xs text-muted-foreground">{l.outstanding} outstanding</span>
                          </div>
                          <div>
                            <Label className="text-xs">Qty</Label>
                            <Input
                              type="number"
                              min="0"
                              max={l.outstanding}
                              value={receipts[l.poItemId]?.qty ?? ''}
                              onChange={(e) => setReceipt(l.poItemId, { qty: e.target.value })}
                              className="mt-1"
                            />
                          </div>
                          <div>
                            <Label className="text-xs">Lot no.</Label>
                            <Input
                              value={receipts[l.poItemId]?.lotNo ?? ''}
                              onChange={(e) => setReceipt(l.poItemId, { lotNo: e.target.value })}
                              className="mt-1"
                            />
                          </div>
                          <div>
                            <Label className="text-xs">Expiry</Label>
                            <Input
                              type="date"
                              value={receipts[l.poItemId]?.expiryDate ?? ''}
                              onChange={(e) => setReceipt(l.poItemId, { expiryDate: e.target.value })}
                              className="mt-1"
                            />
                          </div>
                        </div>
                      ))}
                    <Button onClick={onReceive} disabled={receive.isPending}>
                      <PackageCheck className="mr-1 h-4 w-4" /> Receive into warehouse
                    </Button>
                  </CardContent>
                </Card>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
