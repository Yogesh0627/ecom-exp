package com.ecoexpress.ai.exception;

import com.ecoexpress.common.exception.ApiExceptions.ApiException;
import org.springframework.http.HttpStatus;

/**
 * An AI call failed. 503 SERVICE_UNAVAILABLE, not 500: the AI is an external dependency, and a
 * client should treat "the AI is down" differently from "the server is broken" — usually by
 * retrying or falling back to a non-AI path.
 */
public class AiException extends ApiException {

    private final int gatewayStatus;
    private final int retryAfterSeconds;

    public AiException(String message) {
        this(message, 0, 0);
    }

    public AiException(String message, int gatewayStatus) {
        this(message, gatewayStatus, 0);
    }

    public AiException(String message, int gatewayStatus, int retryAfterSeconds) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message);
        this.gatewayStatus = gatewayStatus;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** The upstream HTTP status, if the failure came from the provider (0 otherwise). */
    public int getGatewayStatus() {
        return gatewayStatus;
    }

    /** Provider-suggested cooldown before retrying (0 if unknown). */
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean isRateLimited() {
        return gatewayStatus == 429;
    }
}
