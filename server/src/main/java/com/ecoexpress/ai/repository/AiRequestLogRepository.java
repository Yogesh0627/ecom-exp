package com.ecoexpress.ai.repository;

import com.ecoexpress.ai.domain.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, UUID> {

    /** Total spend since a cutoff — drives the monthly-budget guard. */
    @Query("""
            SELECT coalesce(sum(l.costInr), 0) FROM AiRequestLog l
            WHERE l.createdAt >= :since AND l.status = com.ecoexpress.ai.domain.AiRequestStatus.SUCCESS
            """)
    BigDecimal spendSince(@Param("since") Instant since);

    @Query("SELECT count(l) FROM AiRequestLog l WHERE l.createdAt >= :since")
    long countSince(@Param("since") Instant since);

    /** Per-feature usage since a cutoff: [feature, calls, tokensIn, tokensOut, costInr]. */
    @Query("""
            SELECT l.feature, count(l), coalesce(sum(l.tokensIn), 0), coalesce(sum(l.tokensOut), 0),
                   coalesce(sum(l.costInr), 0)
            FROM AiRequestLog l
            WHERE l.createdAt >= :since
            GROUP BY l.feature
            ORDER BY count(l) DESC
            """)
    List<Object[]> usageByFeatureSince(@Param("since") Instant since);
}
