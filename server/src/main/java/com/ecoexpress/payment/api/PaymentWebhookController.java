package com.ecoexpress.payment.api;

import com.ecoexpress.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay's webhook endpoint.
 *
 * <p>Unauthenticated by necessity — Razorpay cannot present a JWT. The HMAC signature is
 * the ONLY thing standing between this endpoint and anyone on the internet marking orders
 * as paid, which is why {@link PaymentWebhookService} verifies before it believes.
 */
@Slf4j
@Tag(name = "Payments")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    /**
     * Takes the body as a raw String, NOT a parsed DTO.
     *
     * <p>The signature is an HMAC over the exact bytes Razorpay sent. Binding to an object
     * and re-serialising changes key order and whitespace, so the HMAC would never match
     * and every genuine webhook would be rejected as a forgery.
     *
     * <p>Always returns 200 for anything we have durably recorded — including duplicates
     * and bad signatures. A non-2xx makes Razorpay retry, and retrying a duplicate or a
     * forgery achieves nothing but noise. Genuine processing failures throw, which yields
     * a 500 and a retry, which is what we want.
     */
    @Operation(summary = "Razorpay webhook (public; authenticated by HMAC signature)")
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        boolean processed = webhookService.handle(rawBody, signature);
        return ResponseEntity.ok(processed ? "processed" : "ignored");
    }
}
