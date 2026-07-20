package com.ecoexpress.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email delivery config ({@code ecoexpress.email.*}). Disabled (a no-op) until an API key is set,
 * so the app runs and records notifications even with no provider.
 */
@ConfigurationProperties(prefix = "ecoexpress.email")
public class EmailProperties {

    /** Resend API key. Blank -> email sending is skipped. */
    private String resendApiKey = "";
    /** RFC 5322 from header. Uses the verified sending domain; override with EMAIL_FROM. */
    private String from = "Eco Express <noreply@mail.yogeshchauhan.dev>";
    /** Storefront page the verification link points at; {@code ?token=...} is appended. */
    private String verifyUrl = "http://localhost:3000/verify-email";

    public boolean isEnabled() {
        return resendApiKey != null && !resendApiKey.isBlank();
    }

    public String getResendApiKey() { return resendApiKey; }
    public void setResendApiKey(String resendApiKey) { this.resendApiKey = resendApiKey; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getVerifyUrl() { return verifyUrl; }
    public void setVerifyUrl(String verifyUrl) { this.verifyUrl = verifyUrl; }
}
