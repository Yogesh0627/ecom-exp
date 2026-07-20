package com.ecoexpress.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One AI call, with its token usage and cost (V7). Append-only.
 *
 * <p>This row is what makes AI spend visible <b>before</b> the monthly bill, not after. The PRD
 * puts Gemini on the hot path of five features; without per-call cost accounting, a runaway prompt
 * or an abused endpoint is invisible until it is expensive. Every call through {@code AiService}
 * writes one of these, success or failure.
 *
 * <p>Extends no base class (the table has only created_at/created_by, no version/updated/deleted).
 * Needs {@code @EntityListeners} for {@code @CreatedDate} to fire.
 */
@Entity
@Table(name = "ai_request_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Null for a system call not tied to a user. */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false)
    private AiFeature feature;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "provider", nullable = false)
    @Builder.Default
    private String provider = "GEMINI";

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    /** Estimated rupee cost of this call. */
    @Column(name = "cost_inr", precision = 10, scale = 4)
    private BigDecimal costInr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AiRequestStatus status;

    @Column(name = "error")
    private String error;

    /** What the call was about — ('FRIDGE_SCAN', scanId), ('MEAL_PLAN', planId). */
    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private UUID refId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
