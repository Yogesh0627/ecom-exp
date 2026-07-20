'use client';

import { useState } from 'react';
import { CreditCard, Loader2 } from 'lucide-react';
import { paymentApi } from '@/api';
import { openRazorpayCheckout } from '@/lib';
import { useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui';

/**
 * Pays (or retries paying) a PENDING_PAYMENT order via Razorpay. The order settles to PAID from the
 * server webhook — a successful modal here only submits the payment.
 */
export function PayNowButton({ orderId, onSubmitted }: { orderId: string; onSubmitted?: () => void }) {
  const { user } = useAuth();
  const { toast } = useToast();
  const [busy, setBusy] = useState(false);

  const onPay = async () => {
    setBusy(true);
    try {
      const intent = await paymentApi.createIntent(orderId);
      const resp = await openRazorpayCheckout(intent, { name: user?.fullName, email: user?.email });
      await paymentApi.verifyCallback(resp).catch(() => undefined);
      toast({ variant: 'success', title: 'Payment received', description: "We're confirming your order." });
      onSubmitted?.();
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'dismissed') return; // shopper closed the modal
      const status = (e as { response?: { status?: number } })?.response?.status;
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      toast({
        variant: status === 400 ? 'default' : 'destructive',
        title: status === 400 ? 'Payments unavailable' : 'Payment could not start',
        description: msg ?? 'Please try again.',
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button onClick={onPay} disabled={busy}>
      {busy ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <CreditCard className="mr-1 h-4 w-4" />}
      Pay now
    </Button>
  );
}
