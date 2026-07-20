package com.ecoexpress.inventory.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Purchase-order lifecycle (po_status_chk in V3).
 *
 * <p>Transitions are a state machine, same reasoning as the order one: a status field
 * without enforced transitions eventually receives against a cancelled PO or re-submits a
 * received one.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    SUBMITTED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED;

    public boolean isTerminal() {
        return this == RECEIVED || this == CANCELLED;
    }

    /** Receiving stock is only allowed against a submitted or partially-received PO. */
    public boolean canReceive() {
        return this == SUBMITTED || this == PARTIALLY_RECEIVED;
    }

    public Set<PurchaseOrderStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CANCELLED);
            // A partially-received PO can still be cancelled (supplier cannot fulfil the rest).
            case PARTIALLY_RECEIVED -> EnumSet.of(RECEIVED, CANCELLED);
            case RECEIVED, CANCELLED -> EnumSet.noneOf(PurchaseOrderStatus.class);
        };
    }

    public boolean canTransitionTo(PurchaseOrderStatus target) {
        return allowedNext().contains(target);
    }
}
