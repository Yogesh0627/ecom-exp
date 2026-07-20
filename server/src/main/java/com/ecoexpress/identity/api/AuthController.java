package com.ecoexpress.identity.api;

import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.identity.dto.AuthDtos.AuthResponse;
import com.ecoexpress.identity.dto.AuthDtos.ChangeEmailRequest;
import com.ecoexpress.identity.dto.AuthDtos.LoginRequest;
import com.ecoexpress.identity.dto.AuthDtos.OAuthExchangeRequest;
import com.ecoexpress.identity.dto.AuthDtos.ProfileResponse;
import com.ecoexpress.identity.dto.AuthDtos.RefreshRequest;
import com.ecoexpress.identity.dto.AuthDtos.RegisterRequest;
import com.ecoexpress.identity.dto.AuthDtos.UpdateProfileRequest;
import com.ecoexpress.identity.dto.AuthDtos.VerifyEmailRequest;
import com.ecoexpress.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new customer account")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest http) {
        AuthResponse response = authService.register(request, userAgent(http), clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, userAgent(http), clientIp(http)));
    }

    @Operation(summary = "Exchange a refresh token for a new token pair")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(
                authService.refresh(request.refreshToken(), userAgent(http), clientIp(http)));
    }

    @Operation(summary = "Verify an email address from the link we emailed")
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Resend the verification email to the signed-in user")
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.resendVerification(user.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get my profile")
    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(authService.getProfile(user.getId()));
    }

    @Operation(summary = "Update my name and/or phone")
    @PatchMapping("/me")
    public ResponseEntity<ProfileResponse> updateMe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(user.getId(), request));
    }

    @Operation(summary = "Change my email (a confirmation link is sent to the new address)")
    @PostMapping("/change-email")
    public ResponseEntity<Void> changeEmail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangeEmailRequest request) {
        authService.changeEmail(user.getId(), request);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Cancel a pending email change")
    @PostMapping("/cancel-email-change")
    public ResponseEntity<ProfileResponse> cancelEmailChange(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(authService.cancelEmailChange(user.getId()));
    }

    @Operation(summary = "Exchange a one-time OAuth code (from the Google redirect) for a token pair")
    @PostMapping("/oauth/exchange")
    public ResponseEntity<AuthResponse> oauthExchange(
            @Valid @RequestBody OAuthExchangeRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(
                authService.exchangeOAuthCode(request.code(), userAgent(http), clientIp(http)));
    }

    @Operation(summary = "Sign out of this device")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sign out of every device")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logoutAll(user.getId());
        return ResponseEntity.noContent().build();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    /**
     * Behind a proxy the socket address is the proxy, so prefer X-Forwarded-For.
     *
     * <p>This header is client-controllable and must never be trusted for authorization
     * — it is stored for audit only. It is only meaningful once a trusted proxy is
     * actually in front of the app and overwriting it.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return isStorableIp(first) ? first : null;
        }
        String remote = request.getRemoteAddr();
        return isStorableIp(remote) ? remote : null;
    }

    /**
     * The column is INET; a malformed value would fail the insert and take the whole
     * login down with it. An unparseable address is not worth failing auth over.
     */
    private boolean isStorableIp(String value) {
        if (value == null || value.isBlank() || value.length() > 45) {
            return false;
        }
        try {
            java.net.InetAddress.getByName(value);
            return true;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
