package com.ecoexpress.engagement.domain;

import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.common.domain.BaseEntity;
import com.ecoexpress.identity.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A product review (V6).
 *
 * <p>One review per user per product (a partial unique index enforces it) — otherwise one
 * account could flood a product's rating.
 *
 * <p><b>{@code verifiedPurchase} is never client-supplied.</b> It is set by the server only
 * when the reviewer has a DELIVERED order line for this product. A review site where anyone can
 * self-claim "verified purchase" is worthless; the badge only means something if the system
 * proves it.
 *
 * <p>Reviews are moderated: they start PENDING and only an APPROVED review shows on the product
 * page. {@code moderated_at} and {@code moderated_by} move together (a CHECK enforces it).
 */
@Entity
@Table(name = "reviews")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * The delivered order line that justifies the verified badge. Nullable: an order line can
     * be soft-deleted, and the badge is derived from this being non-null, not asserted by hand.
     */
    @Column(name = "order_item_id")
    private java.util.UUID orderItemId;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    /** Set by the server from a DELIVERED order line, never from the request. */
    @Column(name = "verified_purchase", nullable = false)
    @Builder.Default
    private Boolean verifiedPurchase = false;

    @Column(name = "helpful_count", nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private User moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderation_note")
    private String moderationNote;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    @Builder.Default
    private List<ReviewImage> images = new ArrayList<>();
}
