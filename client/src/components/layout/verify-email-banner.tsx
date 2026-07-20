'use client';

import { useState } from 'react';
import { MailWarning } from 'lucide-react';
import { authApi } from '@/api';
import { useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';

/** A thin nudge for signed-in users who haven't confirmed their email yet, with a resend action. */
export function VerifyEmailBanner() {
  const { isAuthenticated, user } = useAuth();
  const { toast } = useToast();
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  if (!isAuthenticated || !user || user.emailVerified) return null;

  const resend = async () => {
    setSending(true);
    try {
      await authApi.resendVerification();
      setSent(true);
      toast({ variant: 'success', title: 'Verification email sent', description: `Check ${user.email}.` });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not send. Try again shortly.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="flex flex-wrap items-center justify-center gap-2 bg-warning-subtle px-4 py-2 text-center text-sm text-warning">
      <MailWarning className="h-4 w-4" />
      <span>Please confirm your email to secure your account.</span>
      {!sent && (
        <button
          onClick={resend}
          disabled={sending}
          className="font-semibold underline underline-offset-2 disabled:opacity-60"
        >
          {sending ? 'Sending…' : 'Resend email'}
        </button>
      )}
    </div>
  );
}
