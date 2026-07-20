package com.ecoexpress.payment.repository;

import com.ecoexpress.payment.domain.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    Optional<PaymentRefund> findByGatewayRefundId(String gatewayRefundId);

    List<PaymentRefund> findByPaymentId(UUID paymentId);
}
