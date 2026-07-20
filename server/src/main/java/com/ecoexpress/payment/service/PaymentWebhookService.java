package com.ecoexpress.payment.service;

import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.domain.OrderStatus;
import com.ecoexpress.order.service.OrderService;
import com.ecoexpress.payment.domain.Payment;
import com.ecoexpress.payment.domain.PaymentMethod;
import com.ecoexpress.payment.domain.PaymentRefund;
import com.ecoexpress.payment.domain.PaymentStatus;
import com.ecoexpress.payment.domain.PaymentWebhookEvent;
import com.ecoexpress.payment.domain.RefundStatus;
import com.ecoexpress.payment.repository.PaymentRefundRepository;
import com.ecoexpress.payment.repository.PaymentRepository;
import com.ecoexpress.payment.repository.PaymentWebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Handles Razorpay webhooks.
 *
 * <p>Three rules govern this class, and each exists because breaking it costs real money:
 *
 * <ol>
 *   <li><b>Verify before believing.</b> The endpoint is public. An unsigned or
 *       wrongly-signed payload is stored for forensics and then ignored — never acted on.</li>
 *   <li><b>Store, then process.</b> The raw event is persisted before any effect. A crash
 *       mid-handler leaves an unprocessed row that can be replayed, rather than money that
 *       moved with no record of why.</li>
 *   <li><b>Idempotency is the database's job.</b> UNIQUE(gateway, gateway_event_id) means
 *       a retried event fails to insert. Razorpay retries aggressively — on timeouts, on
 *       any non-2xx, on its own schedule — so "we already handled this" cannot be a
 *       best-effort check in application code.</li>
 * </ol>
 *
 * <p>Amounts arrive in <b>paise</b>. Treating 50000 paise as ₹50,000 instead of ₹500 is a
 * 100x error, so conversion happens in exactly one place: {@link #paiseToRupees}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final String GATEWAY = "RAZORPAY";
    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentWebhookEventRepository eventRepository;
    private final RazorpaySignatureVerifier verifier;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /**
     * @param rawBody the EXACT bytes received — re-serialised JSON will not match the HMAC
     * @return true if accepted; false for a duplicate or a bad signature
     */
    @Transactional
    public boolean handle(String rawBody, String signatureHeader) {
        boolean signatureValid = verifier.verifyWebhook(rawBody, signatureHeader);

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Webhook body is not valid JSON — ignoring");
            return false;
        }

        String eventType = root.path("event").asText(null);
        String eventId = extractEventId(root, rawBody);
        if (eventType == null || eventId == null) {
            log.warn("Webhook missing event type or id — ignoring");
            return false;
        }

        // Verify BEFORE the idempotency key is consumed. If an invalid event were stored
        // under the real event id, a forged payload could pre-empt the genuine webhook:
        // the attacker records event X, and Razorpay's real event X is then rejected as a
        // duplicate. Invalid events are still recorded for forensics, but under a
        // synthetic id that can never collide with a real one.
        if (!signatureValid) {
            log.error("Webhook {} ({}) has an INVALID signature — recording for forensics, "
                    + "NOT processing", eventId, eventType);
            PaymentWebhookEvent rejected = PaymentWebhookEvent.builder()
                    .gateway(GATEWAY)
                    // Synthetic, unique key: forgeries must neither collide with each other
                    // nor occupy the idempotency slot a real event needs.
                    .gatewayEventId("INVALID:" + UUID.randomUUID())
                    .eventType(eventType)
                    .payload(rawBody)
                    .signature(signatureHeader)
                    .signatureValid(false)
                    .processingError("Invalid signature")
                    .receivedAt(Instant.now())
                    .build();
            eventRepository.save(rejected);
            return false;
        }

        PaymentWebhookEvent event = PaymentWebhookEvent.builder()
                .gateway(GATEWAY)
                .gatewayEventId(eventId)
                .eventType(eventType)
                .payload(rawBody)
                .signature(signatureHeader)
                .signatureValid(true)
                .receivedAt(Instant.now())
                .build();

        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // The UNIQUE constraint fired: Razorpay retried an event we already processed.
            // This is the guarantee that stops a replayed capture crediting twice.
            log.info("Duplicate webhook {} ({}) — already recorded, ignoring", eventId, eventType);
            return false;
        }

        try {
            switch (eventType) {
                case "payment.captured" -> onPaymentCaptured(root);
                case "payment.failed" -> onPaymentFailed(root);
                case "refund.processed" -> onRefundProcessed(root);
                default -> log.debug("Ignoring unhandled webhook event {}", eventType);
            }
            event.setProcessedAt(Instant.now());
            return true;
        } catch (Exception e) {
            // Record the failure and rethrow: the transaction rolls back the side effects,
            // and returning non-2xx makes Razorpay retry.
            log.error("Failed to process webhook {} ({})", eventId, eventType, e);
            throw e;
        }
    }

    private void onPaymentCaptured(JsonNode root) {
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String paymentId = entity.path("id").asText(null);
        String orderId = entity.path("order_id").asText(null);
        BigDecimal amount = paiseToRupees(entity.path("amount").asLong());

        if (paymentId == null) {
            throw new IllegalArgumentException("payment.captured without a payment id");
        }

        Payment payment = paymentRepository.findByGatewayPaymentIdForUpdate(paymentId)
                .orElseGet(() -> paymentRepository.findByGatewayOrderId(orderId).orElse(null));

        if (payment == null) {
            // A capture for a payment we never created. Do not invent an order — this is
            // either a gateway account mix-up or an attack, and both need a human.
            log.error("payment.captured for unknown payment {} / order {} — no local record",
                    paymentId, orderId);
            throw new IllegalStateException("No local payment for gateway id " + paymentId);
        }

        if (payment.isCaptured()) {
            log.info("Payment {} already captured — nothing to do", paymentId);
            return;
        }

        // Verify the AMOUNT, not just that a payment happened. A capture for less than
        // the order total must not mark it paid.
        if (amount.compareTo(payment.getAmount()) != 0) {
            log.error("Payment {} amount mismatch: gateway says {}, we expected {}",
                    paymentId, amount, payment.getAmount());
            throw new IllegalStateException("Captured amount does not match the order.");
        }

        payment.setGatewayPaymentId(paymentId);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        payment.setMethod(parseMethod(entity.path("method").asText(null)));

        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            orderService.transition(order.getId(), OrderStatus.PAID, null,
                    "Payment captured (" + paymentId + ")");
        }
        log.info("Payment {} captured for order {} — {} {}",
                paymentId, order.getOrderNumber(), amount, payment.getCurrency());
    }

    private void onPaymentFailed(JsonNode root) {
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String paymentId = entity.path("id").asText(null);
        String orderId = entity.path("order_id").asText(null);

        Payment payment = paymentRepository.findByGatewayPaymentIdForUpdate(paymentId)
                .orElseGet(() -> paymentRepository.findByGatewayOrderId(orderId).orElse(null));
        if (payment == null) {
            log.warn("payment.failed for unknown payment {} — ignoring", paymentId);
            return;
        }
        if (payment.isCaptured()) {
            // A failure arriving after a capture is out-of-order delivery, not a reversal.
            // Never un-capture a payment on a webhook ordering quirk.
            log.warn("Ignoring payment.failed for {} — already captured", paymentId);
            return;
        }

        payment.setGatewayPaymentId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureCode(entity.path("error_code").asText(null));
        payment.setFailureReason(entity.path("error_description").asText(null));

        // The order stays PENDING_PAYMENT: a failed attempt is not a cancelled order, and
        // the customer can retry. The expiry job releases the stock if they do not.
        log.info("Payment {} failed: {}", paymentId, payment.getFailureReason());
    }

    private void onRefundProcessed(JsonNode root) {
        JsonNode entity = root.path("payload").path("refund").path("entity");
        String refundId = entity.path("id").asText(null);
        String paymentId = entity.path("payment_id").asText(null);
        BigDecimal amount = paiseToRupees(entity.path("amount").asLong());

        PaymentRefund refund = refundRepository.findByGatewayRefundId(refundId).orElse(null);
        Payment payment = paymentRepository.findByGatewayPaymentIdForUpdate(paymentId)
                .orElse(null);

        if (payment == null) {
            log.error("refund.processed for unknown payment {} — ignoring", paymentId);
            return;
        }

        // A refund on a payment that was never captured is nonsensical, and setting a
        // refunded status on a row with captured_at = null would violate
        // payments_captured_chk (a 500). Refuse cleanly — this indicates upstream
        // corruption, not a normal event.
        if (!payment.isCaptured()) {
            log.error("refund.processed for payment {} which is {} (not captured) — ignoring",
                    paymentId, payment.getStatus());
            return;
        }

        if (refund == null) {
            // Refunded directly in the Razorpay dashboard — no local row exists. Record
            // it rather than dropping it, or our books disagree with the gateway's.
            refund = PaymentRefund.builder()
                    .payment(payment)
                    .gatewayRefundId(refundId)
                    .amount(amount)
                    .currency(payment.getCurrency())
                    .reason(com.ecoexpress.payment.domain.RefundReason.OTHER)
                    .status(RefundStatus.PROCESSED)
                    .notes("Created from a gateway-side refund (no local request)")
                    .processedAt(Instant.now())
                    .build();
            refundRepository.save(refund);
            log.warn("Recorded a dashboard-initiated refund {} for payment {}", refundId, paymentId);
        } else if (refund.getStatus() == RefundStatus.PROCESSED) {
            log.info("Refund {} already processed — nothing to do", refundId);
            return;
        } else {
            refund.setStatus(RefundStatus.PROCESSED);
            refund.setProcessedAt(Instant.now());
        }

        BigDecimal totalRefunded = payment.getAmountRefunded().add(amount);
        if (totalRefunded.compareTo(payment.getAmount()) > 0) {
            // payments_refund_chk would reject this anyway. Refunding more than was
            // charged means our accounting is wrong somewhere upstream.
            throw new IllegalStateException(
                    "Refunds for payment " + paymentId + " would exceed the amount charged.");
        }

        payment.setAmountRefunded(totalRefunded);
        payment.setStatus(totalRefunded.compareTo(payment.getAmount()) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);

        log.info("Refund {} processed: {} of {} for payment {}",
                refundId, amount, payment.getAmount(), paymentId);
    }

    /**
     * Razorpay sends amounts in the smallest currency unit. 50000 paise is ₹500.00, not
     * ₹50,000 — getting this wrong is a 100x error in either direction.
     */
    private BigDecimal paiseToRupees(long paise) {
        return BigDecimal.valueOf(paise).divide(PAISE_PER_RUPEE, 2, java.math.RoundingMode.HALF_UP);
    }

    private PaymentMethod parseMethod(String method) {
        if (method == null) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            // A method we do not model yet must not fail the whole capture.
            log.warn("Unrecognised payment method '{}' — leaving it null", method);
            return null;
        }
    }

    /**
     * Razorpay's {@code x-razorpay-event-id} header is the true event id, but it is not
     * in the body. Fall back to the payment/refund entity id plus the event type, which
     * is stable across retries of the same event.
     */
    private String extractEventId(JsonNode root, String rawBody) {
        String event = root.path("event").asText("");
        JsonNode payment = root.path("payload").path("payment").path("entity").path("id");
        if (!payment.isMissingNode()) {
            return event + ":" + payment.asText();
        }
        JsonNode refund = root.path("payload").path("refund").path("entity").path("id");
        if (!refund.isMissingNode()) {
            return event + ":" + refund.asText();
        }
        return null;
    }
}
