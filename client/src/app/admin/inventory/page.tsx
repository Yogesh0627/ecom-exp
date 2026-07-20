'use client';

import { useState } from 'react';
import { Plus, Warehouse as WarehouseIcon, AlertTriangle, ShieldCheck, PackagePlus } from 'lucide-react';
import {
  useWarehouses,
  useCreateWarehouse,
  useLowStock,
  useLedgerDrift,
  useReceiveStock,
} from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { VariantOption } from '@/types';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
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
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  DatePicker,
} from '@/components/ui';
import { AdminPageHeader, DataState, StatCard, VariantPicker } from '@/components/admin';

export default function AdminInventoryPage() {
  const { data: warehouses, isLoading: whLoading, error: whError } = useWarehouses();
  const { data: lowStock, isLoading: lsLoading } = useLowStock();
  const { data: drift } = useLedgerDrift();
  const createWh = useCreateWarehouse();
  const receive = useReceiveStock();
  const { toast } = useToast();

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ code: '', name: '', city: '', state: '', pincode: '' });

  // Receive-stock dialog state.
  const [rxOpen, setRxOpen] = useState(false);
  const [rxVariant, setRxVariant] = useState<VariantOption | null>(null);
  const [rxWarehouse, setRxWarehouse] = useState('');
  const [rxLot, setRxLot] = useState('');
  const [rxQty, setRxQty] = useState('');
  const [rxCost, setRxCost] = useState('');
  const [rxExpiry, setRxExpiry] = useState('');

  const resetRx = () => {
    setRxVariant(null);
    setRxWarehouse('');
    setRxLot('');
    setRxQty('');
    setRxCost('');
    setRxExpiry('');
  };

  const rxValid = rxVariant && rxWarehouse && rxLot.trim() && Number(rxQty) > 0 && Number(rxCost) >= 0;

  const onReceive = async () => {
    if (!rxVariant) return;
    try {
      await receive.mutateAsync({
        variantId: rxVariant.variantId,
        warehouseId: rxWarehouse,
        lotNo: rxLot.trim(),
        qty: Number(rxQty),
        costPrice: Number(rxCost),
        expiryDate: rxExpiry || undefined,
      });
      toast({
        variant: 'success',
        title: 'Stock received',
        description: `${rxQty} × ${rxVariant.sku}`,
      });
      setRxOpen(false);
      resetRx();
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not receive stock.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const onCreate = async () => {
    try {
      await createWh.mutateAsync({
        code: form.code.trim(),
        name: form.name.trim(),
        city: form.city.trim() || undefined,
        state: form.state.trim() || undefined,
        pincode: form.pincode.trim() || undefined,
      });
      toast({ variant: 'success', title: 'Warehouse created', description: form.code });
      setOpen(false);
      setForm({ code: '', name: '', city: '', state: '', pincode: '' });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not create the warehouse.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const driftCount = drift?.length ?? 0;

  return (
    <div className="space-y-8">
      <AdminPageHeader
        title="Inventory"
        description="Warehouses, low-stock alerts, and ledger integrity."
        action={
          <>
            <Dialog open={rxOpen} onOpenChange={setRxOpen}>
              <DialogTrigger asChild>
                <Button variant="outline" disabled={!warehouses || warehouses.length === 0}>
                  <PackagePlus className="mr-1 h-4 w-4" /> Receive stock
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Receive stock</DialogTitle>
                </DialogHeader>
                <div className="space-y-3">
                  <div>
                    <Label className="text-xs">Variant</Label>
                    <div className="mt-1">
                      <VariantPicker selected={rxVariant} onSelect={setRxVariant} />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <Label className="text-xs">Warehouse</Label>
                      <Select
                        value={rxWarehouse || 'ALL'}
                        onValueChange={(v) => setRxWarehouse(v === 'ALL' ? '' : v)}
                      >
                        <SelectTrigger className="mt-1">
                          <SelectValue placeholder="— Select —" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ALL">— Select —</SelectItem>
                          {(warehouses ?? []).map((w) => (
                            <SelectItem key={w.id} value={w.id}>
                              {w.code}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div>
                      <Label className="text-xs">Lot / batch no.</Label>
                      <Input value={rxLot} onChange={(e) => setRxLot(e.target.value)} placeholder="LOT-2026-07" className="mt-1" />
                    </div>
                    <div>
                      <Label className="text-xs">Quantity</Label>
                      <Input type="number" min="1" value={rxQty} onChange={(e) => setRxQty(e.target.value)} className="mt-1" />
                    </div>
                    <div>
                      <Label className="text-xs">Cost / unit (₹)</Label>
                      <Input type="number" min="0" step="0.01" value={rxCost} onChange={(e) => setRxCost(e.target.value)} className="mt-1" />
                    </div>
                    <div className="col-span-2">
                      <Label className="text-xs">Expiry date (optional)</Label>
                      <DatePicker value={rxExpiry} onChange={setRxExpiry} className="mt-1" />
                    </div>
                  </div>
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setRxOpen(false)}>
                    Cancel
                  </Button>
                  <Button onClick={onReceive} disabled={!rxValid || receive.isPending}>
                    {receive.isPending ? 'Receiving…' : 'Receive'}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>

            <Dialog open={open} onOpenChange={setOpen}>
              <DialogTrigger asChild>
                <Button>
                  <Plus className="mr-1 h-4 w-4" /> New warehouse
                </Button>
              </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>New warehouse</DialogTitle>
              </DialogHeader>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label className="text-xs">Code</Label>
                  <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="BLR-01" className="mt-1" />
                </div>
                <div>
                  <Label className="text-xs">Name</Label>
                  <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Bengaluru DC" className="mt-1" />
                </div>
                <div>
                  <Label className="text-xs">City</Label>
                  <Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} className="mt-1" />
                </div>
                <div>
                  <Label className="text-xs">State</Label>
                  <Input value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} className="mt-1" />
                </div>
                <div>
                  <Label className="text-xs">Pincode</Label>
                  <Input value={form.pincode} onChange={(e) => setForm({ ...form, pincode: e.target.value })} className="mt-1" />
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setOpen(false)}>
                  Cancel
                </Button>
                <Button onClick={onCreate} disabled={!form.code.trim() || !form.name.trim() || createWh.isPending}>
                  {createWh.isPending ? 'Creating…' : 'Create'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
          </>
        }
      />

      {/* Health tiles */}
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Warehouses" value={warehouses?.length ?? '—'} icon={WarehouseIcon} />
        <StatCard
          label="Low-stock alerts"
          value={lowStock?.length ?? '—'}
          icon={AlertTriangle}
          tone={(lowStock?.length ?? 0) > 0 ? 'warning' : 'default'}
        />
        <StatCard
          label="Ledger drift"
          value={driftCount}
          icon={ShieldCheck}
          tone={driftCount > 0 ? 'destructive' : 'success'}
          hint="Cached stock vs replayed ledger — should be 0"
        />
      </div>

      {/* Warehouses */}
      <div>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
          Warehouses
        </h2>
        <DataState
          isLoading={whLoading}
          error={whError}
          isEmpty={!warehouses || warehouses.length === 0}
          emptyLabel="No warehouses yet. Create one to start receiving stock."
        />
        {warehouses && warehouses.length > 0 && (
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Code</TableHead>
                    <TableHead>Name</TableHead>
                    <TableHead>Location</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {warehouses.map((w) => (
                    <TableRow key={w.id}>
                      <TableCell className="font-mono text-sm font-medium">{w.code}</TableCell>
                      <TableCell>{w.name}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {[w.city, w.state].filter(Boolean).join(', ') || '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>

      {/* Low stock */}
      <div>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
          Low stock
        </h2>
        <DataState
          isLoading={lsLoading}
          isEmpty={!lowStock || lowStock.length === 0}
          emptyLabel="All stock is above its reorder point."
        />
        {lowStock && lowStock.length > 0 && (
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>SKU</TableHead>
                    <TableHead>Warehouse</TableHead>
                    <TableHead className="text-right">On hand</TableHead>
                    <TableHead className="text-right">Reserved</TableHead>
                    <TableHead className="text-right">Available</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {lowStock.map((s) => (
                    <TableRow key={s.inventoryId}>
                      <TableCell className="font-mono text-xs">
                        {s.sku}
                        <Badge variant="warning" className="ml-2">
                          reorder
                        </Badge>
                      </TableCell>
                      <TableCell>{s.warehouseCode}</TableCell>
                      <TableCell className="text-right tabular-nums">{s.onHand}</TableCell>
                      <TableCell className="text-right tabular-nums">{s.reserved}</TableCell>
                      <TableCell className="text-right tabular-nums font-medium">{s.available}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
