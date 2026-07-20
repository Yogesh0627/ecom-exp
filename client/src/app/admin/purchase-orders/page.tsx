'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Plus, Trash2, Building2 } from 'lucide-react';
import { ROUTES } from '@/constants';
import { dayjs, formatCurrency } from '@/lib';
import {
  usePurchaseOrders,
  useSuppliers,
  useCreateSupplier,
  useWarehouses,
  useCreatePurchaseOrder,
} from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { PoStatus, VariantOption } from '@/types';
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
import { AdminPageHeader, DataState, statusLabel, VariantPicker } from '@/components/admin';

const PO_STATUS_TONE: Record<PoStatus, 'default' | 'secondary' | 'success' | 'warning' | 'destructive'> = {
  DRAFT: 'secondary',
  SUBMITTED: 'default',
  PARTIALLY_RECEIVED: 'warning',
  RECEIVED: 'success',
  CANCELLED: 'destructive',
};

const PO_STATUSES: PoStatus[] = ['DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'];

interface LineDraft {
  variant: VariantOption;
  qty: string;
  unitCost: string;
}

export default function AdminPurchaseOrdersPage() {
  const [status, setStatus] = useState<PoStatus | ''>('');
  const [page, setPage] = useState(0);
  const { data, isLoading, error } = usePurchaseOrders(status, page);
  const { data: suppliers } = useSuppliers();
  const { data: warehouses } = useWarehouses();
  const createPo = useCreatePurchaseOrder();
  const { toast } = useToast();

  const [open, setOpen] = useState(false);
  const [supplierId, setSupplierId] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [expectedAt, setExpectedAt] = useState('');
  const [lines, setLines] = useState<LineDraft[]>([]);
  const [picker, setPicker] = useState<VariantOption | null>(null);

  const addLine = () => {
    if (!picker) return;
    if (lines.some((l) => l.variant.variantId === picker.variantId)) {
      toast({ variant: 'destructive', title: 'Already added', description: picker.sku });
      return;
    }
    setLines((ls) => [...ls, { variant: picker, qty: '1', unitCost: String(picker.price) }]);
    setPicker(null);
  };

  const setLine = (i: number, patch: Partial<LineDraft>) =>
    setLines((ls) => ls.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));

  const resetForm = () => {
    setSupplierId('');
    setWarehouseId('');
    setExpectedAt('');
    setLines([]);
    setPicker(null);
  };

  const valid =
    supplierId && warehouseId && lines.length > 0 && lines.every((l) => Number(l.qty) > 0 && Number(l.unitCost) >= 0);

  const onCreate = async () => {
    try {
      const po = await createPo.mutateAsync({
        supplierId,
        warehouseId,
        expectedAt: expectedAt || undefined,
        lines: lines.map((l) => ({
          variantId: l.variant.variantId,
          qty: Number(l.qty),
          unitCost: Number(l.unitCost),
        })),
      });
      toast({ variant: 'success', title: 'Purchase order created', description: po.poNumber });
      setOpen(false);
      resetForm();
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not create the purchase order.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const rows = data?.content ?? [];
  const noSuppliers = !suppliers || suppliers.length === 0;
  const noWarehouses = !warehouses || warehouses.length === 0;

  return (
    <div>
      <AdminPageHeader
        title="Purchase Orders"
        description="Order stock from suppliers and receive it into a warehouse."
        action={
          <>
            <SuppliersDialog />
            <Dialog open={open} onOpenChange={setOpen}>
              <DialogTrigger asChild>
                <Button disabled={noSuppliers || noWarehouses}>
                  <Plus className="mr-1 h-4 w-4" /> New PO
                </Button>
              </DialogTrigger>
              <DialogContent className="max-w-2xl">
                <DialogHeader>
                  <DialogTitle>New purchase order</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <Label className="text-xs">Supplier</Label>
                      <select
                        value={supplierId}
                        onChange={(e) => setSupplierId(e.target.value)}
                        className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      >
                        <option value="">— Select —</option>
                        {(suppliers ?? []).map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.name}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <Label className="text-xs">Warehouse</Label>
                      <select
                        value={warehouseId}
                        onChange={(e) => setWarehouseId(e.target.value)}
                        className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      >
                        <option value="">— Select —</option>
                        {(warehouses ?? []).map((w) => (
                          <option key={w.id} value={w.id}>
                            {w.code}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="col-span-2">
                      <Label className="text-xs">Expected delivery (optional)</Label>
                      <Input type="date" value={expectedAt} onChange={(e) => setExpectedAt(e.target.value)} className="mt-1" />
                    </div>
                  </div>

                  {/* Line builder */}
                  <div className="rounded-lg border p-3">
                    <Label className="text-xs">Add a line</Label>
                    <div className="mt-1 flex gap-2">
                      <div className="flex-1">
                        <VariantPicker selected={picker} onSelect={setPicker} />
                      </div>
                      <Button type="button" variant="outline" onClick={addLine} disabled={!picker}>
                        Add
                      </Button>
                    </div>
                  </div>

                  {lines.length > 0 && (
                    <div className="space-y-2">
                      {lines.map((l, i) => (
                        <div key={l.variant.variantId} className="flex items-center gap-2 text-sm">
                          <span className="min-w-0 flex-1 truncate">
                            <span className="font-mono text-xs text-muted-foreground">{l.variant.sku}</span>{' '}
                            {l.variant.productName}
                          </span>
                          <Input
                            type="number"
                            min="1"
                            value={l.qty}
                            onChange={(e) => setLine(i, { qty: e.target.value })}
                            className="w-20"
                            aria-label="Quantity"
                          />
                          <Input
                            type="number"
                            min="0"
                            step="0.01"
                            value={l.unitCost}
                            onChange={(e) => setLine(i, { unitCost: e.target.value })}
                            className="w-24"
                            aria-label="Unit cost"
                          />
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => setLines((ls) => ls.filter((_, idx) => idx !== i))}
                          >
                            <Trash2 className="h-4 w-4 text-muted-foreground" />
                          </Button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setOpen(false)}>
                    Cancel
                  </Button>
                  <Button onClick={onCreate} disabled={!valid || createPo.isPending}>
                    {createPo.isPending ? 'Creating…' : 'Create draft'}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          </>
        }
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
        {PO_STATUSES.map((s) => (
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
        emptyLabel={noSuppliers ? 'Add a supplier first, then raise a purchase order.' : 'No purchase orders yet.'}
      />

      {rows.length > 0 && (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>PO</TableHead>
                  <TableHead>Supplier</TableHead>
                  <TableHead>Warehouse</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Lines</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((po) => (
                  <TableRow key={po.id}>
                    <TableCell className="font-mono text-sm font-medium">{po.poNumber}</TableCell>
                    <TableCell>{po.supplierName}</TableCell>
                    <TableCell className="text-muted-foreground">{po.warehouseCode}</TableCell>
                    <TableCell>
                      <Badge variant={PO_STATUS_TONE[po.status]} className="capitalize">
                        {statusLabel(po.status)}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{po.lineCount}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCurrency(po.grandTotal)}</TableCell>
                    <TableCell>
                      <Button asChild variant="ghost" size="sm">
                        <Link href={ROUTES.admin.purchaseOrder(po.id)}>Open</Link>
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
            <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

/** Suppliers list + create, in a dialog so it doesn't need its own page. */
function SuppliersDialog() {
  const { data: suppliers } = useSuppliers();
  const create = useCreateSupplier();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ code: '', name: '', gstin: '', fssaiLicense: '', city: '', state: '' });

  const onCreate = async () => {
    try {
      await create.mutateAsync({
        code: form.code.trim(),
        name: form.name.trim(),
        gstin: form.gstin.trim() || undefined,
        fssaiLicense: form.fssaiLicense.trim() || undefined,
        city: form.city.trim() || undefined,
        state: form.state.trim() || undefined,
      });
      toast({ variant: 'success', title: 'Supplier added', description: form.name });
      setForm({ code: '', name: '', gstin: '', fssaiLicense: '', city: '', state: '' });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not add the supplier.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline">
          <Building2 className="mr-1 h-4 w-4" /> Suppliers
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Suppliers</DialogTitle>
        </DialogHeader>

        {suppliers && suppliers.length > 0 && (
          <div className="max-h-40 space-y-1 overflow-y-auto">
            {suppliers.map((s) => (
              <div key={s.id} className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                <span>
                  <span className="font-medium">{s.name}</span>{' '}
                  <span className="font-mono text-xs text-muted-foreground">{s.code}</span>
                </span>
                {s.gstin && <span className="text-xs text-muted-foreground">{s.gstin}</span>}
              </div>
            ))}
          </div>
        )}

        <div className="grid grid-cols-2 gap-3 border-t pt-3">
          <div>
            <Label className="text-xs">Code</Label>
            <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="SUP-01" className="mt-1" />
          </div>
          <div>
            <Label className="text-xs">Name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="mt-1" />
          </div>
          <div>
            <Label className="text-xs">GSTIN (optional)</Label>
            <Input value={form.gstin} onChange={(e) => setForm({ ...form, gstin: e.target.value })} className="mt-1" />
          </div>
          <div>
            <Label className="text-xs">FSSAI licence (optional)</Label>
            <Input value={form.fssaiLicense} onChange={(e) => setForm({ ...form, fssaiLicense: e.target.value })} className="mt-1" />
          </div>
          <div>
            <Label className="text-xs">City</Label>
            <Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} className="mt-1" />
          </div>
          <div>
            <Label className="text-xs">State</Label>
            <Input value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} className="mt-1" />
          </div>
        </div>
        <DialogFooter>
          <Button onClick={onCreate} disabled={!form.code.trim() || !form.name.trim() || create.isPending}>
            {create.isPending ? 'Adding…' : 'Add supplier'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
