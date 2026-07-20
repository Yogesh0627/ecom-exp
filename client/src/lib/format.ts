import { CURRENCY, LOCALE } from '@/constants';

/** Format a rupee amount consistently (₹1,234.00). Accepts number or numeric string from the API. */
export function formatCurrency(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(n)) return '—';
  return new Intl.NumberFormat(LOCALE, {
    style: 'currency',
    currency: CURRENCY,
    maximumFractionDigits: 2,
  }).format(n);
}

/** Group a plain count with locale separators (1,234). */
export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat(LOCALE).format(value);
}

/** A 0–100 health score bucketed into a label + tailwind color token. */
export function healthScoreBand(score: number | null | undefined): {
  label: string;
  className: string;
} {
  if (score === null || score === undefined) {
    return { label: 'Not scored', className: 'text-muted-foreground' };
  }
  if (score >= 70) return { label: 'Good', className: 'text-success' };
  if (score >= 45) return { label: 'Moderate', className: 'text-warning' };
  return { label: 'Low', className: 'text-destructive' };
}
