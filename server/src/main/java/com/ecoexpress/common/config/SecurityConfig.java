package com.ecoexpress.common.config;

import com.ecoexpress.common.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
// Enables @PreAuthorize on service/controller methods — how per-permission checks
// like @PreAuthorize("hasAuthority('product:write')") are enforced across modules.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final CorsProperties corsProperties;
    // OAuth2 login beans. The client registration only exists under the "google-oauth" profile, so
    // it is resolved via ObjectProvider — the app boots (without Google login) when it is absent.
    private final org.springframework.beans.factory.ObjectProvider<
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository>
            clientRegistrationRepositoryProvider;
    private final com.ecoexpress.common.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository
            cookieAuthorizationRequestRepository;
    private final com.ecoexpress.common.security.oauth.OAuth2SuccessHandler oauth2SuccessHandler;
    private final com.ecoexpress.common.security.oauth.OAuth2FailureHandler oauth2FailureHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No CSRF token to protect: the API is stateless and authenticates from
                // an Authorization header, which a cross-site form post cannot set.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: auth entry points.
                        .requestMatchers("/api/v1/auth/register",
                                         "/api/v1/auth/login",
                                         "/api/v1/auth/refresh",
                                         // The verification token in the link IS the credential.
                                         "/api/v1/auth/verify-email",
                                         // OAuth: the browser starts the flow and Google calls back
                                         // here; the code-exchange endpoint carries a one-time code,
                                         // not a token. All are safe to reach without a JWT.
                                         "/api/v1/auth/oauth/**",
                                         "/oauth2/**",
                                         "/login/oauth2/**").permitAll()
                        // Public by necessity: Razorpay cannot present a JWT. This route
                        // is NOT unprotected — it authenticates the caller by HMAC
                        // signature over the raw body, and refuses to act on anything
                        // that fails verification.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        // Public: storefront browsing (PRD §10 — browse before login).
                        // Availability is included: a product page must show "in stock"
                        // to a signed-out visitor. It exposes a count for a variant id
                        // and nothing else — no cost, no supplier, no warehouse.
                        .requestMatchers(HttpMethod.GET,
                                         "/api/v1/products/**",
                                         "/api/v1/categories/**",
                                         "/api/v1/banners/**",
                                         "/api/v1/inventory/availability/**",
                                         "/api/v1/reviews/product/**",
                                         "/api/v1/recommendations/variant/**",
                                         // Uploaded assets (product images, organic certificates) are
                                         // served publicly — the storefront shows them to signed-out
                                         // visitors. Upload (POST /admin/files) still needs staff auth.
                                         "/api/v1/files/**",
                                         "/api/v1/settings/public").permitAll()
                        // Public: docs + liveness + the storefront's readiness ping + AI status.
                        .requestMatchers(HttpMethod.GET, "/api/v1/ready", "/api/v1/ai/status").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                         "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Everything else — including every admin route — requires a token.
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::unauthorized)
                        .accessDeniedHandler(this::forbidden))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        // Google login is wired only when its client registration is present (the "google-oauth"
        // profile). The authorization request lives in a cookie, not a session, so the app stays
        // stateless; the success handler redirects to the storefront with a one-time code.
        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(a -> a
                            .authorizationRequestRepository(cookieAuthorizationRequestRepository))
                    .successHandler(oauth2SuccessHandler)
                    .failureHandler(oauth2FailureHandler));
        }

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Explicit origins, never "*": credentialed requests with a wildcard origin are
        // rejected by browsers anyway, and a wildcard on a real API is an open door.
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void unauthorized(jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response,
                              org.springframework.security.core.AuthenticationException ex)
            throws java.io.IOException {
        writeError(response, 401, "UNAUTHORIZED", "Authentication required.");
    }

    private void forbidden(jakarta.servlet.http.HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException ex)
            throws java.io.IOException {
        writeError(response, 403, "FORBIDDEN", "You do not have permission to do that.");
    }

    /** JSON errors, so an API client never has to parse Spring's HTML error page. */
    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("code", code, "message", message, "status", status));
    }
}
