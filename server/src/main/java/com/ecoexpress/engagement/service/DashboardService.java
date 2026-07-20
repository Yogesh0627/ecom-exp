package com.ecoexpress.engagement.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin dashboard analytics (PRD §6).
 *
 * <p>Aggregates are computed in the database, not by loading rows into memory — a dashboard that
 * pulls every order to Java to count them does not survive real volume. Everything here is a
 * single grouped/aggregated query. Revenue counts only genuinely-paid orders, so a cart of
 * unpaid PENDING_PAYMENT orders does not inflate the numbers.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final EntityManager em;

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();

        // Orders by status.
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : this.<Object[]>rows("""
                SELECT status, count(*) FROM orders WHERE deleted_at IS NULL GROUP BY status
                """)) {
            byStatus.put((String) row[0], ((Number) row[1]).longValue());
        }
        out.put("ordersByStatus", byStatus);

        // Revenue from paid-or-later orders only.
        Object[] rev = this.<Object[]>rows("""
                SELECT coalesce(sum(grand_total), 0), count(*)
                FROM orders
                WHERE deleted_at IS NULL
                  AND status IN ('PAID','CONFIRMED','PACKED','SHIPPED','OUT_FOR_DELIVERY','DELIVERED')
                """).get(0);
        out.put("revenue", ((Number) rev[0]).doubleValue());
        out.put("paidOrders", ((Number) rev[1]).longValue());

        // Today's revenue (IST).
        LocalDate today = LocalDate.now(IST);
        Object[] todayRev = this.<Object[]>rows("""
                SELECT coalesce(sum(grand_total), 0), count(*)
                FROM orders
                WHERE deleted_at IS NULL AND placed_at >= :dayStart
                  AND status IN ('PAID','CONFIRMED','PACKED','SHIPPED','OUT_FOR_DELIVERY','DELIVERED')
                """, Map.of("dayStart", today.atStartOfDay(IST).toInstant())).get(0);
        out.put("todayRevenue", ((Number) todayRev[0]).doubleValue());
        out.put("todayOrders", ((Number) todayRev[1]).longValue());

        // Operational counts that need attention.
        out.put("lowStockAlerts", scalarLong("SELECT count(*) FROM low_stock_alerts WHERE resolved_at IS NULL"));
        out.put("pendingReviews", scalarLong("SELECT count(*) FROM reviews WHERE status IN ('PENDING','FLAGGED') AND deleted_at IS NULL"));
        out.put("pendingAdjustments", scalarLong("SELECT count(*) FROM stock_adjustments WHERE approved_at IS NULL AND deleted_at IS NULL"));
        out.put("activeProducts", scalarLong("SELECT count(*) FROM products WHERE status = 'ACTIVE' AND deleted_at IS NULL"));
        out.put("totalCustomers", scalarLong("SELECT count(*) FROM users WHERE deleted_at IS NULL"));

        // Payment reconciliation health: any order whose captured money != its total is a bug.
        out.put("paymentMismatches", scalarLong("""
                SELECT count(*) FROM order_payment_position
                WHERE order_status IN ('PAID','CONFIRMED','PACKED','SHIPPED','DELIVERED')
                  AND net_captured <> grand_total
                """));
        // Inventory ledger drift: should always be 0.
        out.put("ledgerDrift", scalarLong("SELECT count(*) FROM inventory_ledger_drift"));

        return out;
    }

    /** Top sellers by units, from delivered/shipped order lines. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> topProducts(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : this.<Object[]>rows("""
                SELECT oi.product_name_snapshot, sum(oi.qty) AS units, sum(oi.line_total) AS revenue
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.deleted_at IS NULL
                  AND o.status IN ('SHIPPED','OUT_FOR_DELIVERY','DELIVERED')
                GROUP BY oi.product_name_snapshot
                ORDER BY units DESC
                LIMIT :limit
                """, Map.of("limit", limit))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("product", row[0]);
            m.put("units", ((Number) row[1]).longValue());
            m.put("revenue", ((Number) row[2]).doubleValue());
            out.add(m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> rows(String sql) {
        return em.createNativeQuery(sql).getResultList();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> rows(String sql, Map<String, Object> params) {
        var q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q.getResultList();
    }

    private long scalarLong(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
