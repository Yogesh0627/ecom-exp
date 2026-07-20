'use client';

import { useEffect, useState } from 'react';
import { useCreateAddress, useAddressMutations } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { Address } from '@/types';
import type { CreateAddressPayload } from '@/api';
import {
  Button,
  Input,
  Label,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui';

const ADDRESS_TYPES = ['HOME', 'WORK', 'OTHER'];

const EMPTY: CreateAddressPayload = {
  label: '',
  recipientName: '',
  phone: '',
  line1: '',
  line2: '',
  landmark: '',
  city: '',
  state: '',
  pincode: '',
  type: 'HOME',
  isDefault: false,
};

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Pass an address to edit; omit/null to add a new one. */
  initial?: Address | null;
  /** Called with the saved address after a successful create/update. */
  onSaved?: (addr: Address) => void;
  /** Show the "set as default" checkbox (default true). */
  allowDefault?: boolean;
}

/** Shared add/edit-address dialog used by the account page and checkout. */
export function AddressFormDialog({
  open,
  onOpenChange,
  initial,
  onSaved,
  allowDefault = true,
}: Props) {
  const create = useCreateAddress();
  const { update } = useAddressMutations();
  const { toast } = useToast();
  const [form, setForm] = useState<CreateAddressPayload>(EMPTY);

  useEffect(() => {
    if (!open) return;
    setForm(
      initial
        ? {
            label: initial.label ?? '',
            recipientName: initial.recipientName,
            phone: initial.phone,
            line1: initial.line1,
            line2: initial.line2 ?? '',
            landmark: initial.landmark ?? '',
            city: initial.city,
            state: initial.state,
            pincode: initial.pincode,
            type: initial.type,
            isDefault: initial.isDefault,
          }
        : EMPTY,
    );
  }, [open, initial]);

  const set = (f: keyof CreateAddressPayload, v: string | boolean) =>
    setForm((prev) => ({ ...prev, [f]: v }));

  const valid =
    form.recipientName && form.phone && form.line1 && form.city && form.state && form.pincode;

  const save = async () => {
    try {
      const addr = initial
        ? await update.mutateAsync({ id: initial.id, payload: form })
        : await create.mutateAsync(form);
      onOpenChange(false);
      toast({ variant: 'success', title: initial ? 'Address updated' : 'Address added' });
      onSaved?.(addr);
    } catch (e: unknown) {
      toast({
        variant: 'destructive',
        title: 'Could not save address',
        description:
          (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Check the fields and try again.',
      });
    }
  };

  const busy = create.isPending || update.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{initial ? 'Edit address' : 'Add address'}</DialogTitle>
        </DialogHeader>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Label (optional)" value={form.label ?? ''} onChange={(v) => set('label', v)} placeholder="Home, Work…" />
          <div>
            <Label>Type</Label>
            <select
              value={form.type}
              onChange={(e) => set('type', e.target.value)}
              className="mt-1 h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              {ADDRESS_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.charAt(0) + t.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </div>
          <Field label="Recipient name" value={form.recipientName} onChange={(v) => set('recipientName', v)} />
          <Field label="Mobile number" value={form.phone} onChange={(v) => set('phone', v)} />
          <div className="sm:col-span-2">
            <Field label="Address line 1" value={form.line1} onChange={(v) => set('line1', v)} />
          </div>
          <div className="sm:col-span-2">
            <Field label="Address line 2 (optional)" value={form.line2 ?? ''} onChange={(v) => set('line2', v)} />
          </div>
          <Field label="Landmark (optional)" value={form.landmark ?? ''} onChange={(v) => set('landmark', v)} />
          <Field label="City" value={form.city} onChange={(v) => set('city', v)} />
          <Field label="State" value={form.state} onChange={(v) => set('state', v)} />
          <Field label="Pincode" value={form.pincode} onChange={(v) => set('pincode', v)} />
        </div>
        {allowDefault && (
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={!!form.isDefault}
              onChange={(e) => set('isDefault', e.target.checked)}
              className="h-4 w-4"
            />
            Set as default delivery address
          </label>
        )}
        <DialogFooter>
          <Button onClick={save} disabled={!valid || busy}>
            {busy ? 'Saving…' : 'Save address'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div>
      <Label>{label}</Label>
      <Input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} className="mt-1" />
    </div>
  );
}
