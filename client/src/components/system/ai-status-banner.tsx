'use client';

import { AlertTriangle } from 'lucide-react';
import { useAiStatus } from '@/hooks';

/**
 * Shows a clear notice when AI features are temporarily unavailable — over the provider's
 * usage/quota limit, or not configured — so users understand why an AI action isn't working
 * instead of hitting a generic error. Renders nothing when AI is available.
 */
export function AiStatusBanner({ className }: { className?: string }) {
  const { data } = useAiStatus();
  if (!data || data.available) return null;

  const retry =
    data.rateLimited && data.retryAfterSeconds > 0
      ? ` Try again in about ${data.retryAfterSeconds}s.`
      : '';

  return (
    <div
      role="status"
      className={`flex items-start gap-2 rounded-lg border border-warning/40 bg-warning-subtle/50 px-4 py-3 text-sm ${className ?? ''}`}
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
      <p>
        <span className="font-medium">
          {data.rateLimited ? 'AI features are busy right now' : 'AI features are unavailable'}
        </span>
        {' — '}
        {data.message ??
          (data.rateLimited
            ? 'the daily AI limit has been reached.'
            : 'not configured on this environment.')}
        {retry}
      </p>
    </div>
  );
}
