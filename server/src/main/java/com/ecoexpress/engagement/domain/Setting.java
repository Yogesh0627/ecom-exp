package com.ecoexpress.engagement.domain;

import com.ecoexpress.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A runtime configuration value (V6). What ops changes without a deploy: delivery fee,
 * free-shipping threshold, cart reservation TTL, AI budget.
 *
 * <p>{@code value} is JSONB so a setting can be a scalar or a structure without a schema change.
 * {@code isPublic} gates whether the storefront may read it — the AI budget is not the customer's
 * business, the delivery fee is.
 *
 * <p>Extends {@link AuditableEntity} (no soft delete): a setting is updated or deleted outright,
 * never soft-deleted.
 */
@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Setting extends AuditableEntity {

    @Column(name = "key", nullable = false, unique = true)
    private String key;

    /** JSONB. Stored as a JSON string; scalars are stored as JSON literals (e.g. {@code 40}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(name = "description")
    private String description;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;
}
