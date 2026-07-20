package com.ecoexpress.payment.repository;

import com.ecoexpress.payment.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    Optional<PaymentWebhookEvent> findByGatewayAndGatewayEventId(String gateway, String eventId);

    boolean existsByGatewayAndGatewayEventId(String gateway, String eventId);

    /** Events stored but never handled — a crash mid-processing leaves rows here. */
    List<PaymentWebhookEvent> findByProcessedAtIsNullOrderByReceivedAtAsc();
}
