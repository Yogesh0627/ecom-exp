package com.ecoexpress.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request/response payloads for /api/v1/auth. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // 8 is the floor, not the goal. Length beats composition rules, so there is
            // no "must contain a symbol" theatre here — just a real minimum.
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^(\\+91)?[6-9][0-9]{9}$",
                     message = "must be a valid Indian mobile number")
            String phone) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record RefreshRequest(
            @NotBlank String refreshToken) {}

    /** The one-time code from the OAuth redirect, exchanged for a real JWT session. */
    public record OAuthExchangeRequest(
            @NotBlank String code) {}

    /** The email-verification token from the link we emailed. */
    public record VerifyEmailRequest(
            @NotBlank String token) {}

    /**
     * @param accessToken  short-lived JWT
     * @param refreshToken opaque, rotated on every use
     * @param expiresIn    access token lifetime in seconds
     */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserSummary user) {

        public static AuthResponse of(String access, String refresh, long expiresIn, UserSummary user) {
            return new AuthResponse(access, refresh, "Bearer", expiresIn, user);
        }
    }

    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            boolean emailVerified,
            List<String> roles,
            List<String> permissions) {}

    /** The signed-in user's own profile (GET /auth/me) — includes phone and any pending email change. */
    public record ProfileResponse(
            UUID id,
            String email,
            String fullName,
            String phone,
            boolean emailVerified,
            String pendingEmail,
            boolean oauthOnly,
            List<String> roles) {}

    /** Edit name and/or phone. Null fields are left unchanged. */
    public record UpdateProfileRequest(
            @Size(max = 120) String fullName,
            @Pattern(regexp = "^(\\+91)?[6-9][0-9]{9}$",
                     message = "must be a valid Indian mobile number")
            String phone) {}

    /** Request to change email — a verification link goes to the NEW address before it takes effect. */
    public record ChangeEmailRequest(
            @NotBlank @Email @Size(max = 254) String newEmail) {}
}
