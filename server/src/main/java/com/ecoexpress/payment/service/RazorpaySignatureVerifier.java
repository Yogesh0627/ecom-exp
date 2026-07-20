package com.ecoexpress.payment.service;

import com.ecoexpress.payment.config.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies Razorpay HMAC-SHA256 signatures.
 *
 * <p>This class is the security boundary for money. Everything downstream — crediting an
 * order, releasing goods — trusts that a payload reaching it genuinely came from Razorpay.
 * Anyone can POST to a public webhook URL; the signature is the only thing separating a
 * real capture from a forged one.
 *
 * <p>Two different secrets are involved, and confusing them silently breaks verification:
 * <ul>
 *   <li><b>Checkout callback</b> (browser redirect): HMAC of
 *       {@code order_id + "|" + payment_id} keyed with the API <b>key secret</b>.</li>
 *   <li><b>Webhook</b> (server-to-server): HMAC of the <b>raw request body</b> keyed with
 *       the <b>webhook secret</b>.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RazorpayProperties props;

    /**
     * Verifies a webhook against the raw body.
     *
     * <p>The body must be the EXACT bytes received. Re-serialising a parsed object
     * changes key order and whitespace, so the HMAC no longer matches and every real
     * webhook is rejected as a forgery.
     */
    public boolean verifyWebhook(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        if (props.webhookSecret() == null || props.webhookSecret().isBlank()) {
            // Fail closed. A missing secret must never mean "accept everything" — that
            // would turn a misconfigured deploy into an open endpoint for crediting orders.
            log.error("Webhook signature rejected: ecoexpress.razorpay.webhook-secret is not set.");
            return false;
        }
        return matches(rawBody, signatureHeader, props.webhookSecret());
    }

    /** Verifies the browser checkout callback. */
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId,
                                          String signature) {
        if (razorpayOrderId == null || razorpayPaymentId == null || signature == null) {
            return false;
        }
        if (props.keySecret() == null || props.keySecret().isBlank()) {
            log.error("Payment signature rejected: ecoexpress.razorpay.key-secret is not set.");
            return false;
        }
        return matches(razorpayOrderId + "|" + razorpayPaymentId, signature, props.keySecret());
    }

    private boolean matches(String payload, String expectedHex, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            byte[] expected;
            try {
                expected = HexFormat.of().parseHex(expectedHex.trim().toLowerCase());
            } catch (IllegalArgumentException e) {
                // Not valid hex — malformed or hostile input, not a real signature.
                return false;
            }

            // Constant-time. String.equals short-circuits on the first differing byte,
            // which leaks how much of a guess was right and lets an attacker forge a
            // signature byte by byte over many requests.
            return MessageDigest.isEqual(computed, expected);
        } catch (Exception e) {
            log.error("Signature verification failed unexpectedly", e);
            return false;
        }
    }
}
