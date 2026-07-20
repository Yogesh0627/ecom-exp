import { api } from '@/lib';
import type { PaymentIntent, RazorpayCheckoutResponse } from '@/types';

/** Payments. The intent creates the Razorpay order server-side; the callback verify is UI-only. */
export const paymentApi = {
  async createIntent(orderId: string): Promise<PaymentIntent> {
    const { data } = await api.post<PaymentIntent>(`/payments/orders/${orderId}/intent`);
    return data;
  },
  /**
   * Verifies the browser checkout callback so the UI can show success — this does NOT settle the
   * order (the signed webhook does that server-side).
   */
  async verifyCallback(resp: RazorpayCheckoutResponse): Promise<{ verified: boolean }> {
    const { data } = await api.post<{ verified: boolean }>('/payments/callback/verify', {
      razorpayOrderId: resp.razorpay_order_id,
      razorpayPaymentId: resp.razorpay_payment_id,
      razorpaySignature: resp.razorpay_signature,
    });
    return data;
  },
};
