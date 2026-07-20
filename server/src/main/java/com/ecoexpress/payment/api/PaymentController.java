package com.ecoexpress.payment.api;

import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.identity.repository.UserRepository;
import com.ecoexpress.payment.domain.RefundReason;
import com.ecoexpress.payment.service.PaymentService;
import com.ecoexpress.payment.service.PaymentService.PaymentIntent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    public record CallbackRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {}

    public record RefundRequest(
            @NotNull UUID paymentId,
            @NotNull @Positive BigDecimal amount,
            @NotNull RefundReason reason,
            String notes) {}

    @Operation(summary = "Create a payment intent for an order")
    @PostMapping("/orders/{orderId}/intent")
    public ResponseEntity<PaymentIntent> createIntent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.createIntent(orderId, user.getId()));
    }

    /**
     * Verifies the browser checkout callback so the UI can show success or failure.
     *
     * <p>This does NOT mark the order paid — the webhook does, from a signed
     * server-to-server event. A valid signature here only proves the redirect is genuine.
     */
    @Operation(summary = "Verify a checkout callback (does not settle the order)")
    @PostMapping("/callback/verify")
    public ResponseEntity<Map<String, Boolean>> verifyCallback(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CallbackRequest request) {
        boolean valid = paymentService.verifyCheckoutCallback(
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());
        return ResponseEntity.ok(Map.of("verified", valid));
    }

    @Operation(summary = "Request a refund (ops)")
    @PostMapping("/refunds")
    @PreAuthorize("hasAuthority('order:refund')")
    public ResponseEntity<Map<String, Object>> refund(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody RefundRequest request) {
        var user = userRepository.findById(actor.getId()).orElse(null);
        var refund = paymentService.requestRefund(
                request.paymentId(), request.amount(), request.reason(), user, request.notes());
        return ResponseEntity.ok(Map.of(
                "refundId", refund.getId(),
                "status", refund.getStatus().name(),
                "amount", refund.getAmount()));
    }
}
