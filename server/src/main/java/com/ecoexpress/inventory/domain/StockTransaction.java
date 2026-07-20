package com.ecoexpress.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * One movement of stock. The append-only ledger and the source of truth for inventory.
 *
 * <p>Extends neither BaseEntity nor AuditableEntity: the table has no
 * updated_at/updated_by/version/deleted_at, by design. A ledger row is written once and
 * never changed — a database trigger ({@code stock_transactions_append_only}) rejects
 * UPDATE and DELETE outright, so a bug here fails loudly instead of quietly rewriting
 * stock history. Corrections are new reversing rows.
 */
@Entity
@Table(name = "stock_transactions")
// Required for @CreatedDate/@CreatedBy below. BaseEntity and AuditableEntity declare
// this themselves; this class extends neither, so without it the annotations are inert
// and created_at inserts as NULL — caught only by the column's NOT NULL constraint.
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    /** Which batch the movement came from. Null for movements that are not batch-specific. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private StockTransactionType type;

    /** Signed: +receipt, -sale. Never zero (stock_tx_delta_chk). */
    @Column(name = "qty_delta", nullable = false)
    private Integer qtyDelta;

    /**
     * What caused this movement — ('ORDER', orderId), ('PURCHASE_ORDER', poId).
     * Not an FK: it points at different tables depending on the type.
     */
    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private UUID refId;

    @Column(name = "note")
    private String note;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
