package com.ecoexpress.payment.domain;

/**
 * Why money went back (refunds_reason_chk in V5).
 *
 * <p>Reason-coded rather than free text: "how much did we refund for OUT_OF_STOCK last
 * month" is an operations question that free text cannot answer, and for an organic
 * grocery it is the number that tells you whether inventory or quality is leaking money.
 */
public enum RefundReason {
    CUSTOMER_REQUEST,
    /** We took the order and could not fulfil it — an inventory accuracy failure. */
    OUT_OF_STOCK,
    DAMAGED,
    LATE_DELIVERY,
    ORDER_CANCELLED,
    QUALITY_ISSUE,
    OTHER
}
