package com.ecoexpress.common.exception;

import com.ecoexpress.common.exception.ApiExceptions.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into a consistent JSON error shape.
 *
 * <p>Errors the client caused are reported precisely. Anything unexpected returns a
 * generic message — a stack trace or SQL fragment in a response body is an information
 * leak — while the real cause goes to the log.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.getStatus())
                .body(body(ex.getStatus(), ex.getCode(), ex.getMessage(), req));
    }

    /** Bean-validation failures: report every invalid field, not just the first. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        Map<String, String> fieldErrors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        Map<String, Object> body = body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid.", req);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * @PreAuthorize denials.
     *
     * <p>Without this, the catch-all below turns every permission failure into a 500.
     * The request is still correctly blocked — but the client is told "server error"
     * instead of "forbidden", which hides real authorization problems and breaks any
     * client that branches on 403.
     *
     * <p>Spring Security's accessDeniedHandler in SecurityConfig never sees these:
     * @PreAuthorize throws inside the controller invocation, which this advice wraps.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            RuntimeException ex, HttpServletRequest req) {
        // Deliberately terse: naming the missing permission tells a prober exactly what
        // to target.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to do that.", req));
    }

    /**
     * A malformed request body (bad JSON, an unparseable UUID, a wrong enum value) is the
     * CLIENT's fault — a 400, not a 500. Without this handler the catch-all below reports
     * "server error" for what is really "you sent garbage", which is both wrong and alarming
     * on a dashboard. The message is kept generic so a parser's internal detail is not leaked.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                        "The request body could not be read. Check the JSON and field types.", req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "Something went wrong. Please try again.", req));
    }

    private Map<String, Object> body(HttpStatus status, String code, String message,
                                     HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("path", req.getRequestURI());
        return body;
    }
}
