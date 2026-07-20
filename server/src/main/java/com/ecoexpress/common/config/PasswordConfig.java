package com.ecoexpress.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password encoder lives here, not in {@link SecurityConfig}, to keep it off the OAuth wiring
 * path. SecurityConfig depends on the OAuth success handler, which depends on AuthService, which
 * needs the encoder — leaving the bean in SecurityConfig would close that into a circular reference.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt at strength 12. The default (10) is fast enough that a leaked table is worth
     * brute-forcing; 12 costs ~250ms per login — invisible to a user, expensive for an attacker.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
