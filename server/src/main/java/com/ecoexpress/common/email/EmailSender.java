package com.ecoexpress.common.email;

/**
 * Transactional email delivery, behind an interface so the provider is swappable (Resend today).
 * Implementations must be best-effort and non-blocking: an email failure must never break the flow
 * that triggered it (an order still succeeds if its confirmation email bounces).
 */
public interface EmailSender {

    /** Send an HTML email. Runs asynchronously; callers pass primitives, never JPA entities. */
    void send(String toEmail, String subject, String htmlBody);
}
