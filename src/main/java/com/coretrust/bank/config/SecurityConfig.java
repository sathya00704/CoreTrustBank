package com.coretrust.bank.config;

import com.coretrust.bank.security.CustomUserDetailsService;
import com.coretrust.bank.security.JwtAuthenticationFilter;
import com.coretrust.bank.security.JwtService;
import com.coretrust.bank.security.RestAccessDeniedHandler;
import com.coretrust.bank.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/set-password",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    };

    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final HandlerExceptionResolver exceptionResolver;

    public SecurityConfig(JwtService jwtService,
                          CustomUserDetailsService userDetailsService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.exceptionResolver = exceptionResolver;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Created here, not as a @Component: Spring Boot auto-registers Filter beans
        // with the servlet container, which would run it twice.
        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtService, userDetailsService, exceptionResolver);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/accounts/*/unfreeze",
                                "/api/v1/accounts/*/unfreeze/otp").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/accounts/*/unfreeze-requests",
                                "/api/v1/accounts/*/unfreeze-requests/otp").hasRole("SUPPORT")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/unfreeze-requests/*/approve",
                                "/api/v1/unfreeze-requests/*/reject").hasRole("ADMIN")
                        .requestMatchers("/api/v1/assisted/**").hasAnyRole("SUPPORT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/beneficiaries").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/beneficiaries/*").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/transfers").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/transactions/*/reversal-requests").hasRole("SUPPORT")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/reversal-requests/*/approve",
                                "/api/v1/reversal-requests/*/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/transactions/*/disputes").hasRole("SUPPORT")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/disputes/*/resolve",
                                "/api/v1/disputes/*/reject").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit-logs/**").hasAuthority("PERM_VIEW_AUDIT_LOGS")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/users/*/lock",
                                "/api/v1/users/*/unlock").hasAuthority("PERM_MANAGE_USERS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users").hasAnyRole("SUPPORT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/*/resend-setup-link").hasAnyRole("SUPPORT", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/transactions/*/flag",
                                "/api/v1/transactions/*/unflag").hasAuthority("PERM_FLAG_TRANSACTIONS")
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        // Response headers the frontend's JavaScript is allowed to read
        config.setExposedHeaders(List.of("Idempotent-Replayed", "Retry-After", "X-Request-Id", "Content-Disposition"));
        config.setAllowCredentials(false);   // tokens travel in the Authorization header, not cookies
        config.setMaxAge(3600L);             // browsers may cache the preflight answer for an hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}