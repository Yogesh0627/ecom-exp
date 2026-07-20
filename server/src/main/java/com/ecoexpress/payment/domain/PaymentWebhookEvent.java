package com.ecoexpress.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A raw gateway callback, stored before it is acted on (V5).
 *
 * <p>Two jobs:
 * <ul>
 *   <li><b>Idempotency.</b> UNIQUE(gateway, gateway_event_id). Razorpay retries webhooks
 *       — on network timeouts, on non-2xx responses, on its own schedule. Without this
 *       constraint a retried {@code payment.captured} credits the order twice. The
 *       database refuses the duplicate rather than the application remembering to check.</li>
 *   <li><b>Forensics.</b> When a customer disputes a charge months later, the raw payload
 *       is the evidence. It is stored before processing so it survives a crash mid-handler.</li>
 * </ul>
 *
 * <p>No audit columns and no soft delete: the table has none. A DELETE trigger rejects
 * removal.
 */
@Entity
@Table(name = "payment_webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "gateway", nullable = false)
    @Builder.Default
    private String gateway = "RAZORPAY";

    /** The gateway's own event id — the idempotency key. */
    @Column(name = "gateway_event_id", nullable = false)
    private String gatewayEventId;

    /** e.g. payment.captured, payment.failed, refund.processed */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "signature")
    private String signature;

    /**
     * Whether the HMAC matched. Recorded rather than only enforced: a burst of
     * signature_valid = false is someone probing the endpoint, and that is worth seeing.
     */
    @Column(name = "signature_valid")
    private Boolean signatureValid;

    /** Null until handled. Non-null means this event has already had its effect. */
    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_error")
    private String processingError;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();
}
