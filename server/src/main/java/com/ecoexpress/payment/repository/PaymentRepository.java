package com.ecoexpress.payment.repository;

import com.ecoexpress.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /**
     * Locks the payment row while a webhook mutates it.
     *
     * <p>Two Razorpay events for the same payment can land concurrently
     * (payment.captured and refund.processed). Without the lock, both read the same
     * amount_refunded and one overwrites the other — money silently unaccounted for.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.gatewayPaymentId = :id")
    Optional<Payment> findByGatewayPaymentIdForUpdate(@Param("id") String gatewayPaymentId);

    List<Payment> findByOrderId(UUID orderId);

    /**
     * Orders whose captured money does not match their total. Backed by the
     * order_payment_position view (V5). Should always be empty — anything here is a
     * money bug.
     */
    @Query(value = """
            SELECT order_id, order_number, grand_total, captured, refunded, net_captured
            FROM order_payment_position
            WHERE order_status IN ('PAID','CONFIRMED','PACKED','SHIPPED','DELIVERED')
              AND net_captured <> grand_total
            """, nativeQuery = true)
    List<Object[]> findPaymentMismatches();
}
