package com.ecoexpress.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authenticates a request from its Bearer token.
 *
 * <p>Authorities are read from the token's claims rather than reloaded from the
 * database — that is the point of a stateless access token. The tradeoff is that a
 * permission change does not take effect until the token expires (15 minutes). For an
 * immediate lockout, revoke the user's refresh tokens and suspend the account.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);
        // No token is not an error: the endpoint may be public. Authorization decides.
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        jwtService.parseAccessToken(token).ifPresent(claims -> {
            AuthenticatedUser principal = toPrincipal(claims);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        });

        chain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private AuthenticatedUser toPrincipal(Claims claims) {
        List<String> perms = claims.get("perms", List.class);
        Set<GrantedAuthority> authorities = perms == null
                ? Set.of()
                : perms.stream()
                        .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                        .collect(Collectors.toSet());

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                null,   // never carry the password hash on a request principal
                true,
                authorities);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
