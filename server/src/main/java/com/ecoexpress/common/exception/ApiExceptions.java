package com.ecoexpress.common.exception;

import org.springframework.http.HttpStatus;

/** Application exceptions that map to HTTP status codes. */
public final class ApiExceptions {

    private ApiExceptions() {}

    /** Base for anything the API should report with a specific status and code. */
    public static class ApiException extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        public ApiException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getCode() {
            return code;
        }
    }

    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
        }
    }

    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, "CONFLICT", message);
        }
    }

    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) {
            super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
        }
    }

    /**
     * Authentication failure. The message is deliberately vague at every call site —
     * "wrong password" vs "no such user" tells an attacker which emails are registered.
     */
    public static class AuthenticationFailedException extends ApiException {
        public AuthenticationFailedException(String message) {
            super(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message);
        }
    }
}
