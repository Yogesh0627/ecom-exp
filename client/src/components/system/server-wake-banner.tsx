'use client';

import { useEffect, useState } from 'react';
import { API_BASE_URL } from '@/constants';

type Phase = 'checking' | 'warming' | 'ready';

/**
 * Free hosting (Render) spins the server down when idle, so the first request after a quiet spell
 * cold-starts and can take up to a minute — during which every API call just hangs. This polls a
 * tiny readiness endpoint and shows a red→green "waking up" bar so the visitor knows to wait,
 * instead of staring at a frozen page. It stays hidden on a server that's already warm.
 */
export function ServerWakeBanner() {
  const [phase, setPhase] = useState<Phase>('checking');
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let shown = false;

    // Only reveal the bar if the first check is slow — a warm server never flashes it.
    const revealTimer = setTimeout(() => {
      if (!cancelled) {
        shown = true;
        setPhase('warming');
        setVisible(true);
      }
    }, 1200);

    const ping = async (): Promise<boolean> => {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 8000);
      try {
        const res = await fetch(`${API_BASE_URL}/ready`, {
          signal: controller.signal,
          cache: 'no-store',
        });
        return res.ok;
      } catch {
        return false; // cold container: request aborts/fails until it wakes
      } finally {
        clearTimeout(timeout);
      }
    };

    (async () => {
      while (!cancelled) {
        const ok = await ping();
        if (cancelled) return;
        if (ok) {
          clearTimeout(revealTimer);
          if (shown) {
            // We showed the warming bar — flash green, then hide.
            setPhase('ready');
            setVisible(true);
            setTimeout(() => {
              if (!cancelled) setVisible(false);
            }, 2000);
          }
          return;
        }
        shown = true;
        setPhase('warming');
        setVisible(true);
        await new Promise((r) => setTimeout(r, 3000));
      }
    })();

    return () => {
      cancelled = true;
      clearTimeout(revealTimer);
    };
  }, []);

  if (!visible) return null;

  const warming = phase !== 'ready';

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed inset-x-0 top-0 z-[60] flex items-center justify-center gap-2.5 border-b bg-background/95 px-4 py-2 text-center text-sm shadow-sm backdrop-blur"
    >
      <span className="relative flex h-3 w-3 shrink-0">
        {warming && (
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-destructive opacity-75" />
        )}
        <span
          className={`relative inline-flex h-3 w-3 rounded-full ${
            warming ? 'bg-destructive' : 'bg-success'
          }`}
        />
      </span>
      {warming ? (
        <span className="text-muted-foreground">
          <span className="font-medium text-foreground">Waking up the server…</span> free hosting
          sleeps when idle, so the first load can take up to a minute. This light turns green when
          it&apos;s ready — please wait.
        </span>
      ) : (
        <span className="font-medium text-success">Ready — thanks for waiting!</span>
      )}
    </div>
  );
}
