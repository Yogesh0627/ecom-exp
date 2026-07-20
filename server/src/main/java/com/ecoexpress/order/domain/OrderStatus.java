package com.ecoexpress.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle (orders_status_chk in V4).
 *
 * <p>The allowed transitions live here rather than being checked ad hoc at call sites.
 * A status field with no state machine drifts: someone eventually ships a cancelled
 * order or refunds one that was never paid, and each path gets its own half-correct
 * guard.
 */
public enum OrderStatus {
    /** Created, awaiting payment. Stock IS reserved. */
    PENDING_PAYMENT,
    /** Payment captured. */
    PAID,
    /** Accepted by ops. */
    CONFIRMED,
    PACKED,
    /** Stock has physically left; reservation consumed. */
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURNED,
    REFUNDED;

    /** Terminal: nothing follows. */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED;
    }

    /** True while stock is still promised to this order and not yet shipped. */
    public boolean holdsReservation() {
        return this == PENDING_PAYMENT || this == PAID || this == CONFIRMED || this == PACKED;
    }

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(CONFIRMED, CANCELLED, REFUNDED);
            case CONFIRMED -> EnumSet.of(PACKED, CANCELLED, REFUNDED);
            case PACKED -> EnumSet.of(SHIPPED, CANCELLED, REFUNDED);
            // Once goods are with a courier, "cancel" is not a thing — it becomes a
            // return, which is a different physical and financial flow.
            case SHIPPED -> EnumSet.of(OUT_FOR_DELIVERY, RETURNED);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED, RETURNED);
            case DELIVERED -> EnumSet.of(RETURNED);
            case RETURNED -> EnumSet.of(REFUNDED);
            case CANCELLED, REFUNDED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }
}
