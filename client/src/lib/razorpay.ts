import type { PaymentIntent, RazorpayCheckoutResponse } from '@/types';
import { APP_NAME } from '@/constants';

const CHECKOUT_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

interface RazorpayInstance {
  open: () => void;
}
interface RazorpayCtor {
  new (options: Record<string, unknown>): RazorpayInstance;
}
declare global {
  interface Window {
    Razorpay?: RazorpayCtor;
  }
}

/** Loads Razorpay Checkout.js once, on demand (kept off the initial bundle). */
export function loadRazorpayCheckout(): Promise<boolean> {
  if (typeof window === 'undefined') return Promise.resolve(false);
  if (window.Razorpay) return Promise.resolve(true);
  return new Promise((resolve) => {
    const script = document.createElement('script');
    script.src = CHECKOUT_SCRIPT;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

/**
 * Opens the Razorpay checkout modal for a payment intent. Resolves with the gateway response on a
 * successful payment, or rejects if the shopper dismisses the modal or the script fails to load.
 * The order is only marked PAID by the server-side webhook — the resolved response is for UI only.
 */
export async function openRazorpayCheckout(
  intent: PaymentIntent,
  prefill: { name?: string; email?: string },
): Promise<RazorpayCheckoutResponse> {
  const ok = await loadRazorpayCheckout();
  if (!ok || !window.Razorpay) {
    throw new Error('Could not load the payment window. Check your connection and try again.');
  }

  return new Promise<RazorpayCheckoutResponse>((resolve, reject) => {
    const rzp = new window.Razorpay!({
      key: intent.keyId,
      order_id: intent.gatewayOrderId,
      amount: intent.amountPaise,
      currency: intent.currency,
      name: APP_NAME,
      description: `Order ${intent.orderNumber}`,
      prefill: { name: prefill.name, email: prefill.email },
      theme: { color: '#2f8f4e' },
      handler: (response: RazorpayCheckoutResponse) => resolve(response),
      modal: {
        ondismiss: () => reject(new Error('dismissed')),
      },
    });
    rzp.open();
  });
}
