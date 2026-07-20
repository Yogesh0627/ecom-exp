'use client';

import { useState } from 'react';
import { Search, Check } from 'lucide-react';
import { cn, formatCurrency } from '@/lib';
import { useVariantSearch } from '@/hooks';
import type { VariantOption } from '@/types';
import { Input } from '@/components/ui';

/**
 * Type-to-search variant selector. Searches by SKU or product name across all statuses (a manager
 * stocks DRAFT products too). Calls onSelect with the chosen variant; the parent owns the value.
 */
export function VariantPicker({
  selected,
  onSelect,
}: {
  selected?: VariantOption | null;
  onSelect: (v: VariantOption) => void;
}) {
  const [term, setTerm] = useState('');
  const [open, setOpen] = useState(false);
  const { data: results, isFetching } = useVariantSearch(term);

  return (
    <div className="relative">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={term}
          onChange={(e) => {
            setTerm(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder={selected ? `${selected.sku} — ${selected.productName}` : 'Search SKU or product…'}
          className="pl-9"
        />
      </div>

      {open && term.trim().length >= 2 && (
        <div className="absolute z-20 mt-1 max-h-60 w-full overflow-y-auto rounded-md border bg-popover shadow-md">
          {isFetching && (!results || results.length === 0) ? (
            <p className="p-3 text-sm text-muted-foreground">Searching…</p>
          ) : results && results.length > 0 ? (
            results.map((v) => (
              <button
                key={v.variantId}
                type="button"
                onClick={() => {
                  onSelect(v);
                  setTerm('');
                  setOpen(false);
                }}
                className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-accent"
              >
                <span className="min-w-0">
                  <span className="font-mono text-xs text-muted-foreground">{v.sku}</span>{' '}
                  <span className="truncate">{v.productName}</span>
                  <span className="text-muted-foreground"> · {v.variantName}</span>
                </span>
                <span className="shrink-0 tabular-nums text-muted-foreground">
                  {formatCurrency(v.price)}
                </span>
              </button>
            ))
          ) : (
            <p className="p-3 text-sm text-muted-foreground">No variants match.</p>
          )}
        </div>
      )}

      {selected && (
        <p className={cn('mt-1 flex items-center gap-1 text-xs text-success')}>
          <Check className="h-3 w-3" /> {selected.sku} — {selected.productName} ({selected.variantName})
        </p>
      )}
    </div>
  );
}
