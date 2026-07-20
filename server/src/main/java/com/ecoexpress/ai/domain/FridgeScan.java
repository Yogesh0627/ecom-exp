package com.ecoexpress.ai.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A Smart Fridge scan (V7, PRD §5.1): a photo of the user's fridge, what Gemini Vision detected,
 * and — via {@link FridgeScanItem} — the ingredients found.
 *
 * <p><b>PII warning made concrete.</b> These images show the inside of someone's home.
 * {@code purgeAfter} is a compliance control: a retention job deletes the image once it passes.
 * {@code rawResponse} keeps Gemini's output for debugging a misdetection, but the image itself is
 * the sensitive part and is what gets purged.
 */
@Entity
@Table(name = "fridge_scans")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FridgeScan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** S3/MinIO key of the uploaded photo. Purged by the retention job after {@link #purgeAfter}. */
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FridgeScanStatus status = FridgeScanStatus.PENDING;

    @Column(name = "model")
    private String model;

    /** Raw Gemini response, for debugging a misdetection. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "error")
    private String error;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "image_purged_at")
    private Instant imagePurgedAt;

    /** When the retention job may delete the image. Defaults to 30 days in the DDL. */
    @Column(name = "purge_after", nullable = false)
    private Instant purgeAfter;

    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<FridgeScanItem> items = new ArrayList<>();
}
