package com.ecoexpress.ai.domain;

import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One ingredient the fridge scan detected (V7).
 *
 * <p>Extends {@link AuditableEntity} (no soft delete — the table has no deleted_at).
 * {@code confidence} is 0..1; a low-confidence detection is shown to the user for confirmation
 * rather than silently trusted — a wrong ingredient poisons the recipe match. {@code variant} is
 * nullable: the detected item may not map to anything we sell.
 */
@Entity
@Table(name = "fridge_scan_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FridgeScanItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private FridgeScan scan;

    @Column(name = "detected_name", nullable = false)
    private String detectedName;

    /** Model confidence, 0..1. Null when the model gave none. */
    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "quantity_guess")
    private String quantityGuess;

    /** The catalog variant this maps to, if any — the basis for "buy the missing items". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "confirmed_by_user", nullable = false)
    @Builder.Default
    private Boolean confirmedByUser = false;
}
