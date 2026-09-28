package com.coretrust.bank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * PHASE 1 ONLY: everything is open so we can build and test the foundation.
 * Phase 2 replaces this with JWT authentication and role-based rules.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //csrf(disable): CSRF attacks target browser sessions that use cookies.
        //Our API will use JWT in headers, not cookies, so CSRF protection isn't needed.

        //SessionCreationPolicy.STATELESS: the server never creates an HTTP session.
        // Every request must prove who it is, which is how JWT works.

        //anyRequest().permitAll(): all requests are allowed.
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    //hashes passwords before saving
    //identical passwords produce different hashes and brute-forcing is expensive.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
