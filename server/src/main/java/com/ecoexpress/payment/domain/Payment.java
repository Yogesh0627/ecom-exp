package com.ecoexpress.payment.domain;

import com.ecoexpress.order.domain.Order;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment attempt against an order (V5).
 *
 * <p>Extends no base class on purpose: {@code payments} has no {@code version} and no
 * {@code deleted_at}. There is no soft delete because payment history is a legal record —
 * a correction is a refund row, never a deletion. There is no optimistic-lock version
 * because concurrency here is guarded by the UNIQUE constraint on
 * {@code gateway_payment_id}: a replayed webhook loses on insert rather than on a version
 * check.
 *
 * <p>{@code @EntityListeners} is required for the audit annotations below to fire.
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "gateway", nullable = false)
    @Builder.Default
    private String gateway = "RAZORPAY";

    /** Razorpay's order handle (order_xxx), created before the customer pays. */
    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    /**
     * Razorpay's payment handle (pay_xxx). UNIQUE — this is the idempotency key that
     * makes a replayed webhook impossible to double-process.
     */
    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "gateway_signature")
    private String gatewaySignature;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    private PaymentMethod method;

    /** Refunded so far. Can never exceed {@code amount} (payments_refund_chk). */
    @Column(name = "amount_refunded", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amountRefunded = BigDecimal.ZERO;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Set exactly when status becomes CAPTURED (payments_captured_chk). */
    @Column(name = "captured_at")
    private Instant capturedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    /** How much of this payment is still refundable. */
    public BigDecimal refundableAmount() {
        return amount.subtract(amountRefunded);
    }

    public boolean isCaptured() {
        return status == PaymentStatus.CAPTURED
                || status == PaymentStatus.PARTIALLY_REFUNDED;
    }
}
