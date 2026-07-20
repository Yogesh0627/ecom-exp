package com.ecoexpress.payment.service;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.domain.OrderStatus;
import com.ecoexpress.order.repository.OrderRepository;
import com.ecoexpress.payment.config.RazorpayProperties;
import com.ecoexpress.payment.domain.Payment;
import com.ecoexpress.payment.domain.PaymentRefund;
import com.ecoexpress.payment.domain.PaymentStatus;
import com.ecoexpress.payment.domain.RefundReason;
import com.ecoexpress.payment.domain.RefundStatus;
import com.ecoexpress.payment.repository.PaymentRefundRepository;
import com.ecoexpress.payment.repository.PaymentRepository;
import com.ecoexpress.identity.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Creating payment intents and requesting refunds.
 *
 * <p><b>Nothing here trusts the client.</b> The browser's "payment succeeded" callback is
 * a hint that the user got back from the gateway — never proof. An order only becomes PAID
 * from a signed webhook (see {@link PaymentWebhookService}). Treating a redirect as proof
 * of payment is how storefronts ship goods for free: the callback is trivially forged by
 * anyone who can open dev tools.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final RazorpayProperties razorpayProperties;
    private final RazorpaySignatureVerifier verifier;
    private final RazorpayClient razorpayClient;

    /**
     * @param keyId          public key for the browser SDK
     * @param gatewayOrderId Razorpay order handle
     * @param amountPaise    what the SDK expects — the smallest currency unit
     */
    public record PaymentIntent(
            UUID paymentId,
            String keyId,
            String gatewayOrderId,
            long amountPaise,
            String currency,
            String orderNumber) {}

    /**
     * Creates a local payment row for an order.
     *
     * <p>The Razorpay order is NOT created here yet — that needs live credentials. Once
     * they exist, this is the one place that calls the gateway; everything else already
     * works against the local row.
     */
    @Transactional
    public PaymentIntent createIntent(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new NotFoundException("No order " + orderId));

        // Same message as a missing order: confirming someone else's order exists is a leak.
        if (!order.getUser().getId().equals(userId)) {
            throw new NotFoundException("No order " + orderId);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Order " + order.getOrderNumber()
                    + " is " + order.getStatus() + " and is not awaiting payment.");
        }
        if (!razorpayProperties.isConfigured()) {
            // Explicit and honest, rather than a confusing failure deeper in a gateway call.
            throw new BadRequestException(
                    "Payments are not configured yet (ecoexpress.razorpay.key-id / key-secret).");
        }

        // Reuse an existing unpaid intent rather than stacking rows for retries.
        Payment payment = paymentRepository.findByOrderId(orderId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                .findFirst()
                .orElseGet(() -> paymentRepository.save(Payment.builder()
                        .order(order)
                        // The amount comes from the ORDER, never from the client. A
                        // client-supplied amount is a "pay ₹1 for a ₹1000 basket" bug.
                        .amount(order.getGrandTotal())
                        .currency(order.getCurrency())
                        .status(PaymentStatus.CREATED)
                        .build()));

        // Create the gateway order on first intent (idempotent for retries: reuse the handle we
        // already have). This is the one place that talks to Razorpay; the browser then opens
        // checkout against this order id.
        if (payment.getGatewayOrderId() == null || payment.getGatewayOrderId().isBlank()) {
            String gatewayOrderId = razorpayClient.createOrder(
                    toPaise(payment.getAmount()), payment.getCurrency(), order.getOrderNumber());
            payment.setGatewayOrderId(gatewayOrderId);
            log.info("Created Razorpay order {} for {} ({} {})",
                    gatewayOrderId, order.getOrderNumber(), payment.getAmount(), payment.getCurrency());
        }

        return new PaymentIntent(payment.getId(), razorpayProperties.keyId(),
                payment.getGatewayOrderId(), toPaise(payment.getAmount()),
                payment.getCurrency(), order.getOrderNumber());
    }

    /**
     * Verifies the browser checkout callback.
     *
     * <p>A valid signature here proves the user genuinely completed checkout, so it is
     * safe to show them a success page. It deliberately does NOT mark the order paid —
     * only the webhook does that, because a signature proves the redirect is authentic,
     * not that the money settled.
     */
    @Transactional(readOnly = true)
    public boolean verifyCheckoutCallback(String gatewayOrderId, String gatewayPaymentId,
                                          String signature) {
        boolean valid = verifier.verifyPaymentSignature(gatewayOrderId, gatewayPaymentId, signature);
        if (!valid) {
            log.warn("Checkout callback signature INVALID for order {} payment {}",
                    gatewayOrderId, gatewayPaymentId);
        }
        return valid;
    }

    /**
     * Requests a refund. Creates a PENDING row; the gateway call goes here once
     * credentials exist, and {@code refund.processed} flips it to PROCESSED.
     */
    @Transactional
    public PaymentRefund requestRefund(UUID paymentId, BigDecimal amount, RefundReason reason,
                                       User actor, String notes) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("No payment " + paymentId));

        if (!payment.isCaptured()) {
            throw new BadRequestException(
                    "Cannot refund a payment that is " + payment.getStatus() + ".");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Refund amount must be greater than zero.");
        }
        // Checked here for a clean 400; payments_refund_chk is the real guarantee.
        if (amount.compareTo(payment.refundableAmount()) > 0) {
            throw new BadRequestException("Only " + payment.refundableAmount()
                    + " " + payment.getCurrency() + " is left to refund.");
        }

        PaymentRefund refund = refundRepository.save(PaymentRefund.builder()
                .payment(payment)
                .amount(amount)
                .currency(payment.getCurrency())
                .reason(reason)
                .status(RefundStatus.PENDING)
                .initiatedBy(actor)
                .notes(notes)
                .build());

        log.info("Refund requested: {} {} against payment {} ({})",
                amount, payment.getCurrency(), paymentId, reason);
        return refund;
    }

    /**
     * Orders whose captured money does not equal their total. Always expected to be
     * empty — this exists so the books can be checked rather than assumed.
     */
    @Transactional(readOnly = true)
    public List<Object[]> findPaymentMismatches() {
        return paymentRepository.findPaymentMismatches();
    }

    /** Razorpay works in paise. ₹500.00 is 50000. */
    private long toPaise(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100)).longValueExact();
    }
}
