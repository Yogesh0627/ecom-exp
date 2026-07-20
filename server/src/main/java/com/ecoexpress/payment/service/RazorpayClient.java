package com.ecoexpress.payment.service;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.payment.config.RazorpayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Thin Razorpay Orders API client. A direct HTTPS call (no SDK dependency) to the one gateway
 * endpoint we need — creating an order — authenticated with HTTP Basic (key id : key secret). The
 * secret never leaves the server; the browser only ever sees the public key id and the order handle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayClient {

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final RazorpayProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * Creates a Razorpay order and returns its id ({@code order_...}). Auto-captures so a
     * successful payment settles without a separate capture call.
     *
     * @param amountPaise amount in the smallest unit (₹500.00 -> 50000)
     * @param receipt     our order number, echoed back on the gateway order for reconciliation
     */
    public String createOrder(long amountPaise, String currency, String receipt) {
        String payload;
        try {
            payload = mapper.writeValueAsString(Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", receipt,
                    "payment_capture", 1));
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the Razorpay order request", e);
        }

        String auth = Base64.getEncoder().encodeToString(
                (props.keyId() + ":" + props.keySecret()).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(ORDERS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new BadRequestException("Could not reach the payment gateway. Please try again.");
        }

        if (response.statusCode() / 100 != 2) {
            // Do not surface Razorpay's raw error to the client; log it, return a clean message.
            log.error("Razorpay order creation failed ({}): {}", response.statusCode(), response.body());
            throw new BadRequestException("The payment gateway rejected the request. Please try again.");
        }

        try {
            JsonNode node = mapper.readTree(response.body());
            String id = node.path("id").asText(null);
            if (id == null || id.isBlank()) {
                throw new BadRequestException("The payment gateway returned no order id.");
            }
            return id;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("The payment gateway returned an unreadable response.");
        }
    }
}
