package com.ecoexpress.common.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/**
 * Stores the in-flight OAuth2 authorization request in a short-lived cookie instead of the HTTP
 * session. This is what lets the app stay fully stateless (SessionCreationPolicy.STATELESS) while
 * still doing a server-side OAuth2 login: the request survives the redirect to Google and back with
 * no session, and the API keeps authenticating purely from the JWT.
 *
 * <p>The cookie value is a serialized {@link OAuth2AuthorizationRequest}. Deserialization is locked
 * to Spring Security and JDK classes via an {@link ObjectInputFilter}, so a forged cookie cannot
 * pull in an arbitrary gadget class.
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "ECOEXPRESS_OAUTH2_AUTH_REQUEST";
    private static final int TTL_SECONDS = 180;
    private static final ObjectInputFilter SAFE_FILTER =
            ObjectInputFilter.Config.createFilter("org.springframework.security.**;java.**;!*");

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = readCookie(request);
        return cookie == null ? null : deserialize(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(request, response);
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, serialize(authorizationRequest))
                .path("/")
                .httpOnly(true)
                .secure(request.isSecure())
                // Lax so the cookie rides the top-level GET redirect back from Google to our callback.
                .sameSite("Lax")
                .maxAge(TTL_SECONDS)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        if (loaded != null) {
            deleteCookie(request, response);
        }
        return loaded;
    }

    private Cookie readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE_NAME.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .path("/").httpOnly(true).secure(request.isSecure()).sameSite("Lax").maxAge(0).build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private static String serialize(OAuth2AuthorizationRequest obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize the OAuth authorization request", e);
        }
        return Base64.getUrlEncoder().encodeToString(bos.toByteArray());
    }

    private static OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] data = Base64.getUrlDecoder().decode(value);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                ois.setObjectInputFilter(SAFE_FILTER);
                return (OAuth2AuthorizationRequest) ois.readObject();
            }
        } catch (Exception e) {
            // A malformed or tampered cookie just means "no request in flight".
            return null;
        }
    }
}
