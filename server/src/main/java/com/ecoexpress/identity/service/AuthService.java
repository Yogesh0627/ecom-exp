package com.ecoexpress.identity.service;

import com.ecoexpress.common.email.EmailProperties;
import com.ecoexpress.common.email.EmailSender;
import com.ecoexpress.common.exception.ApiExceptions.AuthenticationFailedException;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.ConflictException;
import com.ecoexpress.common.security.JwtService;
import com.ecoexpress.identity.domain.EmailVerificationToken;
import com.ecoexpress.identity.domain.OAuthAccount;
import com.ecoexpress.identity.domain.OAuthProvider;
import com.ecoexpress.identity.domain.RefreshToken;
import com.ecoexpress.identity.domain.Role;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.domain.UserStatus;
import com.ecoexpress.identity.dto.AuthDtos.AuthResponse;
import com.ecoexpress.identity.dto.AuthDtos.LoginRequest;
import com.ecoexpress.identity.dto.AuthDtos.RegisterRequest;
import com.ecoexpress.identity.dto.AuthDtos.ChangeEmailRequest;
import com.ecoexpress.identity.dto.AuthDtos.ProfileResponse;
import com.ecoexpress.identity.dto.AuthDtos.UpdateProfileRequest;
import com.ecoexpress.identity.dto.AuthDtos.UserSummary;
import com.ecoexpress.identity.repository.EmailVerificationTokenRepository;
import com.ecoexpress.identity.repository.OAuthAccountRepository;
import com.ecoexpress.identity.repository.RefreshTokenRepository;
import com.ecoexpress.identity.repository.RoleRepository;
import com.ecoexpress.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Registration, login, refresh-token rotation and logout.
 *
 * <p>The interesting part of this class is {@link #refresh}: see the theft-detection
 * note there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "CUSTOMER";
    /** Deliberately identical for every auth failure — see the note in login(). */
    private static final String GENERIC_AUTH_FAILURE = "Invalid email or password.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final OAuthCodeStore oauthCodeStore;
    private final TokenRevocationService tokenRevocationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent, String ip) {
        // Cheap pre-check for a friendly error. The real guarantee is the unique index
        // and the catch below — two simultaneous registrations both pass this check.
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with that email already exists.");
        }

        Role customerRole = roleRepository.findByNameWithPermissions(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + DEFAULT_ROLE + " is missing — V1 seed did not run."));

        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .status(UserStatus.ACTIVE)
                .build();
        user.addRole(customerRole);

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent registration for the same email.
            throw new ConflictException("An account with that email already exists.");
        }

        log.info("Registered user {}", user.getId());
        sendVerificationEmail(user);
        return issueTokens(user, userAgent, ip);
    }

    /**
     * Creates a fresh verification token and emails the link. The raw token lives only in the email;
     * we store its hash. Best-effort — a failure here never blocks registration (the user can ask
     * for a resend). No-op for an already-verified user.
     */
    @Transactional
    public void sendVerificationEmail(User user) {
        if (user.isEmailVerified() || user.getEmail() == null) {
            return;
        }
        String raw = jwtService.generateRefreshTokenValue();
        verificationTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(jwtService.hashRefreshToken(raw))
                .expiresAt(Instant.now().plus(java.time.Duration.ofHours(24)))
                .build());

        String link = emailProperties.getVerifyUrl() + "?token=" + raw;
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;\
                border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                  <div style="background:#2f8f4e;padding:16px 24px;color:#fff;font-size:18px;font-weight:bold">\
                EcoExpress</div>
                  <div style="padding:24px;color:#111827">
                    <h2 style="margin:0 0 8px;font-size:18px">Confirm your email</h2>
                    <p style="margin:0 0 16px;font-size:14px;line-height:1.6;color:#374151">\
                Welcome to EcoExpress! Please confirm your email to secure your account.</p>
                    <a href="%s" style="display:inline-block;background:#2f8f4e;color:#fff;\
                text-decoration:none;padding:10px 20px;border-radius:8px;font-size:14px;font-weight:bold">\
                Verify email</a>
                    <p style="margin:16px 0 0;font-size:12px;color:#6b7280">\
                Or paste this link: %s<br>This link expires in 24 hours.</p>
                  </div>
                </div>""".formatted(link, link);
        emailSender.send(user.getEmail(), "Confirm your EcoExpress email", html);
    }

    /** Consumes a verification token and marks the user's email verified. Single-use. */
    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = verificationTokenRepository
                .findByTokenHash(jwtService.hashRefreshToken(rawToken))
                .orElseThrow(() -> new BadRequestException(
                        "This verification link is invalid. Request a new one."));
        if (!token.isUsable()) {
            throw new BadRequestException("This verification link has expired or was already used.");
        }
        token.setUsedAt(Instant.now());
        User user = token.getUser();

        if (token.getNewEmail() != null) {
            // Change-email: switch to the new address now that it's proven to be the user's.
            String newEmail = token.getNewEmail().toLowerCase();
            if (!newEmail.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new BadRequestException("That email is now in use by another account.");
            }
            user.setEmail(newEmail);
            user.setPendingEmail(null);
            user.setEmailVerifiedAt(Instant.now());
            log.info("Email changed and verified for user {}", user.getId());
            return;
        }

        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(Instant.now());
            log.info("Email verified for user {}", user.getId());
        }
    }

    /** The signed-in user's own profile. */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Account not found."));
        return toProfile(user);
    }

    /** Update name and/or phone (direct; no verification). Email is changed via {@link #changeEmail}. */
    @Transactional
    public ProfileResponse updateProfile(java.util.UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Account not found."));
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        return toProfile(user);
    }

    /**
     * Starts a verify-before-switch email change: stages the new address and emails IT a link. The
     * current email stays active until the link is clicked (handled in {@link #verifyEmail}).
     */
    @Transactional
    public void changeEmail(java.util.UUID userId, ChangeEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Account not found."));
        if (user.isOAuthOnly()) {
            throw new BadRequestException(
                    "This account signs in with Google; its email can't be changed here.");
        }
        String newEmail = request.newEmail().trim().toLowerCase();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("That's already your email address.");
        }
        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new BadRequestException("That email is already in use.");
        }

        user.setPendingEmail(newEmail);

        String raw = jwtService.generateRefreshTokenValue();
        verificationTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(jwtService.hashRefreshToken(raw))
                .newEmail(newEmail)
                .expiresAt(Instant.now().plus(java.time.Duration.ofHours(24)))
                .build());

        String link = emailProperties.getVerifyUrl() + "?token=" + raw;
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;\
                border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                  <div style="background:#2f8f4e;padding:16px 24px;color:#fff;font-size:18px;font-weight:bold">\
                EcoExpress</div>
                  <div style="padding:24px;color:#111827">
                    <h2 style="margin:0 0 8px;font-size:18px">Confirm your new email</h2>
                    <p style="margin:0 0 16px;font-size:14px;line-height:1.6;color:#374151">\
                Click below to make this your EcoExpress sign-in email. Until you do, your current \
                email stays active.</p>
                    <a href="%s" style="display:inline-block;background:#2f8f4e;color:#fff;\
                text-decoration:none;padding:10px 20px;border-radius:8px;font-size:14px;font-weight:bold">\
                Confirm new email</a>
                    <p style="margin:16px 0 0;font-size:12px;color:#6b7280">\
                Or paste this link: %s<br>This link expires in 24 hours. If you didn't request this, ignore it.</p>
                  </div>
                </div>""".formatted(link, link);
        emailSender.send(newEmail, "Confirm your new EcoExpress email", html);
        log.info("Email-change requested for user {} -> pending verification", user.getId());
    }

    /** Cancels a pending email change: clears the staged address and kills any emailed links. */
    @Transactional
    public ProfileResponse cancelEmailChange(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Account not found."));
        user.setPendingEmail(null);
        verificationTokenRepository.expirePendingChangeTokens(userId, Instant.now());
        log.info("Email change cancelled for user {}", userId);
        return toProfile(user);
    }

    private ProfileResponse toProfile(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new ProfileResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.isEmailVerified(), user.getPendingEmail(),
                user.isOAuthOnly(), roles);
    }

    /** Re-sends verification for a signed-in, still-unverified user. */
    @Transactional
    public void resendVerification(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Account not found."));
        if (user.isEmailVerified()) {
            throw new BadRequestException("Your email is already verified.");
        }
        sendVerificationEmail(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String ip) {
        User user = userRepository.findByEmailWithAuthorities(request.email())
                .orElse(null);

        // Every failure below returns the SAME message. Distinguishing "no such user"
        // from "wrong password" turns the login endpoint into an account enumerator.
        if (user == null) {
            // Burn roughly the same CPU as a real bcrypt check so response time does not
            // leak whether the account exists.
            passwordEncoder.encode("dummy-password-to-equalise-timing");
            throw new AuthenticationFailedException(GENERIC_AUTH_FAILURE);
        }
        if (user.isOAuthOnly()) {
            throw new AuthenticationFailedException(GENERIC_AUTH_FAILURE);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException(GENERIC_AUTH_FAILURE);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Distinct message: this one is not enumeration-sensitive, the caller already
            // proved they own the account by supplying the right password.
            throw new AuthenticationFailedException("This account is " + user.getStatus() + ".");
        }

        user.setLastLoginAt(Instant.now());
        return issueTokens(user, userAgent, ip);
    }

    /**
     * Rotates a refresh token.
     *
     * <p><b>Theft detection.</b> A refresh token is single-use: using it mints a
     * replacement and marks the old row rotated. If a token that has ALREADY been
     * rotated is presented again, two parties hold it — the legitimate client and
     * whoever stole it. There is no way to tell which one is calling, so the safe move
     * is to revoke every token for that user and force a fresh login.
     */
    @Transactional
    public AuthResponse refresh(String rawToken, String userAgent, String ip) {
        String hash = jwtService.hashRefreshToken(rawToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token."));

        if (stored.isRotated()) {
            log.warn("Refresh token replay detected for user {} — revoking all sessions",
                    stored.getUser().getId());
            // REQUIRES_NEW: this MUST commit even though the exception below rolls this
            // transaction back. Revoking inline would be undone by the throw, leaving the
            // stolen token live — the control would silently do nothing.
            tokenRevocationService.revokeAllForUser(stored.getUser().getId());
            throw new AuthenticationFailedException(
                    "This session has been revoked. Please sign in again.");
        }
        if (!stored.isUsable()) {
            throw new AuthenticationFailedException("Refresh token is expired or revoked.");
        }

        User user = userRepository.findByIdWithAuthorities(stored.getUser().getId())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("This account is " + user.getStatus() + ".");
        }

        AuthResponse response = issueTokens(user, userAgent, ip);

        // Link old -> new so a future replay of the old token is detectable.
        RefreshToken replacement = refreshTokenRepository
                .findByTokenHash(jwtService.hashRefreshToken(response.refreshToken()))
                .orElseThrow(() -> new IllegalStateException("Replacement token was not persisted"));
        stored.setReplacedBy(replacement);
        stored.setRevokedAt(Instant.now());

        return response;
    }

    /**
     * Resolves the EcoExpress user behind a verified Google account, creating one on first sign-in.
     *
     * <p>Match order: the Google account id (a returning Google user), then the email (a customer
     * who first registered with a password and is now adding Google), then a brand-new user. The
     * email is trusted as verified because Google asserts it. Returns the user so the OAuth success
     * handler can mint a one-time code against its id.
     */
    @Transactional
    public User upsertOAuthUser(OAuthProvider provider, String providerUserId, String email,
                               String fullName) {
        String normalizedEmail = email == null ? null : email.toLowerCase();

        var existing = oauthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existing.isPresent()) {
            return userRepository.findByIdWithAuthorities(existing.get().getUser().getId())
                    .orElseThrow(() -> new IllegalStateException("OAuth account points at a missing user"));
        }

        User user = normalizedEmail == null ? null
                : userRepository.findByEmailWithAuthorities(normalizedEmail).orElse(null);

        if (user == null) {
            Role customerRole = roleRepository.findByNameWithPermissions(DEFAULT_ROLE)
                    .orElseThrow(() -> new IllegalStateException(
                            "Role " + DEFAULT_ROLE + " is missing — V1 seed did not run."));
            user = User.builder()
                    .email(normalizedEmail)
                    // No password — this is an OAuth-only account (isOAuthOnly() == true). Password
                    // login is refused for it; the customer can set one later via reset if desired.
                    .fullName(fullName == null || fullName.isBlank() ? "EcoExpress Customer" : fullName)
                    // Google has already verified the address, so we do not re-verify it.
                    .emailVerifiedAt(Instant.now())
                    .status(UserStatus.ACTIVE)
                    .build();
            user.addRole(customerRole);
            user = userRepository.saveAndFlush(user);
            log.info("Created user {} from Google sign-in", user.getId());
        }

        try {
            oauthAccountRepository.saveAndFlush(OAuthAccount.builder()
                    .user(user)
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .email(normalizedEmail)
                    .linkedAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent first sign-in for the same Google account — the
            // other transaction linked it. Re-read and use that link.
            return oauthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                    .map(a -> userRepository.findByIdWithAuthorities(a.getUser().getId()).orElseThrow())
                    .orElseThrow(() -> e);
        }
        return user;
    }

    /**
     * Exchanges a one-time OAuth code (from the redirect) for the real JWT session. The code is
     * single-use and short-lived; it references a user but carries no token itself.
     */
    @Transactional
    public AuthResponse exchangeOAuthCode(String code, String userAgent, String ip) {
        java.util.UUID userId = oauthCodeStore.consume(code)
                .orElseThrow(() -> new AuthenticationFailedException(
                        "This sign-in link is invalid or has expired. Please try again."));
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new AuthenticationFailedException("Account not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("This account is " + user.getStatus() + ".");
        }
        user.setLastLoginAt(Instant.now());
        return issueTokens(user, userAgent, ip);
    }

    /** Revokes every session for the user. */
    @Transactional
    public void logoutAll(java.util.UUID userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Revoked {} refresh token(s) for user {}", revoked, userId);
    }

    /** Revokes a single session (this device). */
    @Transactional
    public void logout(String rawToken) {
        String hash = jwtService.hashRefreshToken(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    private AuthResponse issueTokens(User user, String userAgent, String ip) {
        List<String> permissions = permissionsOf(user);
        String access = jwtService.generateAccessToken(user.getId(), user.getEmail(), permissions);

        String rawRefresh = jwtService.generateRefreshTokenValue();
        RefreshToken refresh = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashRefreshToken(rawRefresh))
                .issuedAt(Instant.now())
                .expiresAt(jwtService.refreshTokenExpiry())
                .userAgent(truncate(userAgent, 500))
                .ipAddress(ip)
                .build();
        refreshTokenRepository.saveAndFlush(refresh);

        return AuthResponse.of(access, rawRefresh, jwtService.accessTokenTtlSeconds(),
                toSummary(user, permissions));
    }

    private List<String> permissionsOf(User user) {
        return user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getName())
                .distinct()
                .sorted()
                .toList();
    }

    private UserSummary toSummary(User user, List<String> permissions) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(),
                user.isEmailVerified(), roles, permissions);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
