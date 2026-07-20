package com.ecoexpress.payment.domain;

/**
 * Mirrors payments_method_chk in V5. UPI dominates in India, which is why Razorpay was
 * chosen over a card-first gateway.
 */
public enum PaymentMethod {
    UPI,
    CARD,
    NETBANKING,
    WALLET,
    EMI,
    /** Cash on delivery — no gateway involved. */
    COD
}
