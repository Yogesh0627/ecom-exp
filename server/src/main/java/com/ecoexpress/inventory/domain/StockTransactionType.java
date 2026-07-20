package com.ecoexpress.inventory.domain;

/**
 * Ledger entry types (stock_tx_type_chk in V3).
 *
 * <p>RESERVATION and RELEASE move {@code qty_reserved}, not {@code qty_on_hand} — the
 * goods have not physically moved. Everything else changes on-hand. The
 * {@code inventory_ledger_drift} view relies on this split when it recomputes on-hand
 * from the ledger.
 */
public enum StockTransactionType {
    /** Goods received from a supplier. +on_hand */
    RECEIPT,
    /** Shipped to a customer. -on_hand */
    SALE,
    /** Customer returned goods to stock. +on_hand */
    RETURN,
    /** Reason-coded correction. +/- on_hand */
    ADJUSTMENT,
    TRANSFER_IN,
    TRANSFER_OUT,
    /** Written off. -on_hand */
    DAMAGE,
    /** Past expiry, written off. -on_hand */
    EXPIRY,
    /** Promised to a cart/order. +reserved, on_hand unchanged. */
    RESERVATION,
    /** Reservation cancelled or expired. -reserved, on_hand unchanged. */
    RELEASE;

    /** True when this type moves reserved rather than on-hand. */
    public boolean affectsReservedOnly() {
        return this == RESERVATION || this == RELEASE;
    }
}
