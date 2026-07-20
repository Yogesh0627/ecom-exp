package com.ecoexpress.common.security.oauth;

import com.ecoexpress.identity.domain.OAuthProvider;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.service.AuthService;
import com.ecoexpress.identity.service.OAuthCodeStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Completes a Google sign-in. It resolves (or creates) the EcoExpress user, mints a one-time code
 * against that user, and redirects the browser back to the storefront with only the code — no token
 * ever touches the URL. The storefront then exchanges the code for the JWT.
 */
@Slf4j
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuthCodeStore codeStore;
    private final String successRedirect;

    public OAuth2SuccessHandler(AuthService authService, OAuthCodeStore codeStore,
                                @Value("${ecoexpress.oauth.success-redirect:http://localhost:3000/auth/callback}")
                                String successRedirect) {
        this.authService = authService;
        this.codeStore = codeStore;
        this.successRedirect = successRedirect;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String providerUserId = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");

        User user = authService.upsertOAuthUser(OAuthProvider.GOOGLE, providerUserId, email, name);
        String code = codeStore.issue(user.getId());

        String target = UriComponentsBuilder.fromUriString(successRedirect)
                .queryParam("code", code)
                .build().toUriString();
        response.sendRedirect(target);
    }
}
