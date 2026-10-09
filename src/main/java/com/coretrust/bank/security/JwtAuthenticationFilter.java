package com.coretrust.bank.security;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Objects;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService,
                                   HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.exceptionResolver = exceptionResolver;
    }

    /** Auth endpoints never need an access token (e.g. refreshing with an expired one). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        //reads the Authorization header
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);   // no token: continue as anonymous; security rules decide
            return;
        }
        try {
            JwtService.AccessTokenClaims claims =
                    jwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()));

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                SecurityUser user = userDetailsService.loadUserById(claims.userId());
                if (!user.isAccountNonLocked()) {
                    throw new LockedException("Account is locked");
                }
                if (!user.isEnabled()) {
                    throw new DisabledException("Account is not active");
                }
                // Token belongs to a session that was logged out or replaced by a newer login
                if (!Objects.equals(claims.sessionId(), user.activeSessionId())) {
                    throw new BusinessException(ErrorCode.SESSION_REVOKED,
                            "This session is no longer active (logged out or signed in elsewhere).");
                }

                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException ex) {
            reject(request, response, new BusinessException(ErrorCode.TOKEN_EXPIRED, "Access token has expired."));
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            reject(request, response, new BusinessException(ErrorCode.INVALID_TOKEN, "Access token is invalid."));
            return;
        } catch (AuthenticationException | BusinessException ex) {
            reject(request, response, ex);
            return;
        }

        chain.doFilter(request, response);
    }

    /** Filters run outside controllers, so we hand the error to GlobalExceptionHandler manually. */
    private void reject(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        SecurityContextHolder.clearContext();
        exceptionResolver.resolveException(request, response, null, ex);
    }
}