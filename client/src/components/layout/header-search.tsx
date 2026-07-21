'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, Loader2, Leaf } from 'lucide-react';
import { catalogApi } from '@/api';
import { ROUTES, QUERY_KEYS } from '@/constants';
import { formatCurrency } from '@/lib';
import { useDebounce } from '@/hooks';
import { Input } from '@/components/ui';

/**
 * Search-as-you-type: the query is debounced, results appear in a dropdown without pressing Enter.
 * Enter (or "See all results") opens the full search page.
 */
export function HeaderSearch({
  className,
  onNavigate,
}: {
  className?: string;
  /** Called when the user navigates from a result — lets the mobile drawer close itself. */
  onNavigate?: () => void;
}) {
  const router = useRouter();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const boxRef = useRef<HTMLDivElement>(null);

  const debounced = useDebounce(q.trim(), 300);
  const enabled = debounced.length >= 2;

  const { data, isFetching } = useQuery({
    queryKey: [...QUERY_KEYS.products({ suggest: debounced }), 'suggest'],
    queryFn: () => catalogApi.search(debounced, 0, 6),
    enabled,
    staleTime: 30_000,
  });

  const results = enabled ? data?.content ?? [] : [];

  // Close on outside click.
  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, []);

  const goToSearch = (term: string) => {
    setOpen(false);
    onNavigate?.();
    router.push(ROUTES.search(term.trim()));
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (active >= 0 && results[active]) {
        setOpen(false);
        onNavigate?.();
        router.push(ROUTES.product(results[active].slug));
      } else if (q.trim()) {
        goToSearch(q);
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(i - 1, -1));
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const showDropdown = open && q.trim().length >= 2;

  return (
    <div ref={boxRef} className={`relative ${className ?? ''}`}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder="Search organic groceries…"
          className="pl-9"
          role="combobox"
          aria-expanded={showDropdown}
          aria-controls="header-search-results"
        />
        {isFetching && enabled && (
          <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-muted-foreground" />
        )}
      </div>

      {showDropdown && (
        <div
          id="header-search-results"
          className="absolute left-0 right-0 top-full z-50 mt-2 overflow-hidden rounded-xl border bg-card text-card-foreground shadow-xl ring-1 ring-black/5"
        >
          {results.length > 0 ? (
            <ul className="max-h-[60vh] overflow-y-auto py-1">
              {results.map((p, i) => (
                <li key={p.id}>
                  <Link
                    href={ROUTES.product(p.slug)}
                    onClick={() => {
                      setOpen(false);
                      onNavigate?.();
                    }}
                    onMouseEnter={() => setActive(i)}
                    className={`flex items-center gap-3 px-3 py-2 text-sm ${
                      i === active ? 'bg-accent' : 'hover:bg-accent'
                    }`}
                  >
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted">
                      {p.imageUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={p.imageUrl} alt="" className="h-full w-full object-cover" />
                      ) : (
                        <Leaf className="h-4 w-4 text-muted-foreground opacity-40" />
                      )}
                    </span>
                    <span className="min-w-0 flex-1 truncate font-medium">{p.name}</span>
                    <span className="font-semibold">{formatCurrency(p.price)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          ) : !isFetching ? (
            <p className="px-3 py-4 text-sm text-muted-foreground">
              No matches for “{q.trim()}”.
            </p>
          ) : (
            <p className="px-3 py-4 text-sm text-muted-foreground">Searching…</p>
          )}

          <button
            onClick={() => goToSearch(q)}
            className="w-full border-t px-3 py-2.5 text-left text-sm font-medium text-primary hover:bg-accent"
          >
            See all results for “{q.trim()}”
          </button>
        </div>
      )}
    </div>
  );
}
