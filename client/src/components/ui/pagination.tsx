'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './button';

interface PaginationProps {
  /** Current zero-based page index. */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

/**
 * Shared page navigator for any list backed by a `PageResponse`. Zero-based `page`; hides itself
 * when there's a single page. Shows a compact window of page numbers around the current page.
 */
export function Pagination({ page, totalPages, onPageChange, className }: PaginationProps) {
  if (totalPages <= 1) return null;

  // A small window of numbered pages around the current one.
  const windowSize = 5;
  let start = Math.max(0, page - Math.floor(windowSize / 2));
  const end = Math.min(totalPages, start + windowSize);
  start = Math.max(0, end - windowSize);
  const pages = Array.from({ length: end - start }, (_, i) => start + i);

  return (
    <nav
      className={`flex items-center justify-center gap-1 ${className ?? ''}`}
      aria-label="Pagination"
    >
      <Button
        variant="outline"
        size="icon"
        className="h-8 w-8"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        aria-label="Previous page"
      >
        <ChevronLeft className="h-4 w-4" />
      </Button>

      {start > 0 && (
        <>
          <PageButton n={0} active={page === 0} onClick={onPageChange} />
          {start > 1 && <span className="px-1 text-muted-foreground">…</span>}
        </>
      )}

      {pages.map((p) => (
        <PageButton key={p} n={p} active={p === page} onClick={onPageChange} />
      ))}

      {end < totalPages && (
        <>
          {end < totalPages - 1 && <span className="px-1 text-muted-foreground">…</span>}
          <PageButton n={totalPages - 1} active={page === totalPages - 1} onClick={onPageChange} />
        </>
      )}

      <Button
        variant="outline"
        size="icon"
        className="h-8 w-8"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="Next page"
      >
        <ChevronRight className="h-4 w-4" />
      </Button>
    </nav>
  );
}

function PageButton({
  n,
  active,
  onClick,
}: {
  n: number;
  active: boolean;
  onClick: (n: number) => void;
}) {
  return (
    <Button
      variant={active ? 'default' : 'outline'}
      size="icon"
      className="h-8 w-8 text-sm"
      onClick={() => onClick(n)}
      aria-current={active ? 'page' : undefined}
    >
      {n + 1}
    </Button>
  );
}
