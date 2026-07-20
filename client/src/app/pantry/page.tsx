'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Refrigerator, Plus, Check, Trash2, AlertTriangle } from 'lucide-react';
import { ROUTES } from '@/constants';
import { dayjs } from '@/lib';
import { usePantry, usePantryMutations, useAuth } from '@/hooks';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Badge,
  Skeleton,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  DatePicker,
} from '@/components/ui';
import { AiStatusBanner } from '@/components/system/ai-status-banner';
import type { PantryUnit } from '@/types';

const UNITS: PantryUnit[] = ['PIECE', 'G', 'KG', 'ML', 'L', 'PACK'];

export default function PantryPage() {
  const { isAuthenticated, isReady } = useAuth();
  const { data: items, isLoading } = usePantry();
  const { add, consume, remove } = usePantryMutations();

  const [name, setName] = useState('');
  const [qty, setQty] = useState('1');
  const [unit, setUnit] = useState<PantryUnit>('PIECE');
  const [expiry, setExpiry] = useState('');

  const onAdd = async () => {
    if (!name.trim()) return;
    await add.mutateAsync({
      ingredientName: name.trim(),
      qty: Number(qty) || 1,
      unit,
      expiryDate: expiry || undefined,
    });
    setName('');
    setQty('1');
    setExpiry('');
  };

  if (isReady && !isAuthenticated) {
    return (
      <div className="container py-20 text-center">
        <p className="mb-4 text-muted-foreground">Sign in to manage your pantry.</p>
        <Button asChild>
          <Link href={ROUTES.login}>Sign in</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="container max-w-3xl space-y-6 py-8">
      <AiStatusBanner />
      <div className="space-y-1">
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <Refrigerator className="h-6 w-6 text-primary" /> My pantry
        </h1>
        <p className="text-muted-foreground">
          Track what you have so the app can suggest recipes and avoid recommending things you
          already own.
        </p>
      </div>

      {/* Add form */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Add an item</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 sm:grid-cols-12">
            <div className="sm:col-span-5">
              <Label className="text-xs">Ingredient</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Atta" className="mt-1" />
            </div>
            <div className="sm:col-span-2">
              <Label className="text-xs">Qty</Label>
              <Input value={qty} onChange={(e) => setQty(e.target.value)} type="number" min="0" className="mt-1" />
            </div>
            <div className="sm:col-span-2">
              <Label className="text-xs">Unit</Label>
              <Select value={unit} onValueChange={(v) => setUnit(v as PantryUnit)}>
                <SelectTrigger className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {UNITS.map((u) => (
                    <SelectItem key={u} value={u}>
                      {u}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="sm:col-span-3">
              <Label className="text-xs">Expiry (optional)</Label>
              <DatePicker value={expiry} onChange={setExpiry} placeholder="Pick a date" className="mt-1" />
            </div>
          </div>
          <Button onClick={onAdd} disabled={add.isPending || !name.trim()} className="mt-4">
            <Plus className="mr-1 h-4 w-4" /> Add to pantry
          </Button>
        </CardContent>
      </Card>

      {/* List */}
      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-16 rounded-lg" />
          ))}
        </div>
      ) : items && items.length > 0 ? (
        <div className="space-y-2">
          {items.map((item) => (
            <Card key={item.id}>
              <CardContent className="flex items-center gap-3 p-4">
                <div className="flex-1">
                  <p className="font-medium">{item.ingredientName}</p>
                  <p className="text-xs text-muted-foreground">
                    {item.qty} {item.unit}
                    {item.expiryDate && ` · expires ${dayjs(item.expiryDate).format('LL')}`}
                  </p>
                </div>
                {item.expiringSoon && (
                  <Badge variant="warning" className="gap-1">
                    <AlertTriangle className="h-3 w-3" /> Use soon
                  </Badge>
                )}
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => consume.mutate(item.id)}
                  title="Mark used up"
                >
                  <Check className="mr-1 h-3 w-3" /> Used
                </Button>
                <Button variant="ghost" size="icon" onClick={() => remove.mutate(item.id)}>
                  <Trash2 className="h-4 w-4 text-muted-foreground" />
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            Your pantry is empty. Add what you have at home above.
          </CardContent>
        </Card>
      )}
    </div>
  );
}
