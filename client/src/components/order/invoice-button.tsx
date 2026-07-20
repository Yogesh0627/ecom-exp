'use client';

import { useState } from 'react';
import { FileText, Loader2 } from 'lucide-react';
import { orderApi } from '@/api';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui';

/**
 * Downloads an order's tax-invoice PDF. The endpoint is authenticated (the PDF carries the
 * customer's address), so we fetch it as a blob through the axios instance rather than linking to a
 * URL, then open it in a new tab.
 */
export function InvoiceButton({
  orderId,
  variant = 'outline',
  size = 'sm',
}: {
  orderId: string;
  variant?: 'default' | 'outline' | 'ghost';
  size?: 'default' | 'sm';
}) {
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);

  const onClick = async () => {
    setLoading(true);
    try {
      const blob = await orderApi.invoicePdf(orderId);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      // Revoke after a beat so the new tab has time to load it.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status;
      toast({
        variant: 'destructive',
        title: 'Invoice unavailable',
        description: status === 400 ? 'The invoice is available once the order is paid.' : 'Please try again.',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button variant={variant} size={size} onClick={onClick} disabled={loading}>
      {loading ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <FileText className="mr-1 h-4 w-4" />}
      Invoice
    </Button>
  );
}
