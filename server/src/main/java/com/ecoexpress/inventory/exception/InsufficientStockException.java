package com.ecoexpress.inventory.exception;

import com.ecoexpress.common.exception.ApiExceptions.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Not enough stock to satisfy a reservation.
 *
 * <p>409 CONFLICT, not 400: the request was well-formed, the world just changed —
 * someone else bought the last unit. A client should re-check availability and retry,
 * which is a different reaction than "you sent bad input".
 */
public class InsufficientStockException extends ApiException {

    public InsufficientStockException(String message) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", message);
    }
}
