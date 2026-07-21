package com.ecoexpress.engagement.domain;

import com.ecoexpress.common.domain.BaseEntity;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.identity.domain.User;
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
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * A shopper's request to be notified when a specific out-of-stock variant returns (V13).
 *
 * <p>{@code notifiedAt == null} is the "still waiting" state. Once inventory crosses 0 -> positive
 * the alert is fulfilled (notification raised, {@code notifiedAt} stamped), so a subsequent restock
 * will not re-notify unless the shopper subscribes again.
 */
@Entity
@Table(name = "stock_alerts")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAlert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "notified_at")
    private Instant notifiedAt;
}
