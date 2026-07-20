/** Payment types — mirror the backend PaymentService. */

export interface PaymentIntent {
  paymentId: string;
  keyId: string;
  gatewayOrderId: string;
  amountPaise: number;
  currency: string;
  orderNumber: string;
}

/** The fields Razorpay Checkout hands back to the success handler. */
export interface RazorpayCheckoutResponse {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}
