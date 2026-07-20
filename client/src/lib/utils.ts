import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge Tailwind class names, resolving conflicts (later class wins). The standard shadcn/ui
 * helper: clsx handles conditionals, tailwind-merge de-duplicates conflicting utilities so
 * `cn('p-2', condition && 'p-4')` yields `p-4` rather than both.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
