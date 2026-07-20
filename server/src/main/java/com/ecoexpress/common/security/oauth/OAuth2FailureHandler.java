package com.ecoexpress.common.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/** Sends a failed Google sign-in back to the storefront login with a generic error flag. */
@Slf4j
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final String failureRedirect;

    public OAuth2FailureHandler(
            @Value("${ecoexpress.oauth.failure-redirect:http://localhost:3000/login}") String failureRedirect) {
        this.failureRedirect = failureRedirect;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Google sign-in failed: {}", exception.getMessage());
        String target = UriComponentsBuilder.fromUriString(failureRedirect)
                .queryParam("error", "google_signin_failed")
                .build().toUriString();
        response.sendRedirect(target);
    }
}
