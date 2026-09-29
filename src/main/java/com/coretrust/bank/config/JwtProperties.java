package com.coretrust.bank.config;

import com.coretrust.bank.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        Map<Role, @Valid SessionPolicy> sessions
) {

    /** Fail at startup if any role has no session policy configured. */
    public JwtProperties {
        for (Role role : Role.values()) {
            if (sessions == null || !sessions.containsKey(role)) {
                throw new IllegalStateException("Missing session policy: app.jwt.sessions."
                        + role.name().toLowerCase() + ".token-ttl / max-lifetime");
            }
        }
    }

    public SessionPolicy policyFor(Role role) {
        return sessions.get(role);
    }

    public record SessionPolicy(@NotNull Duration tokenTtl, @NotNull Duration maxLifetime) {

        public SessionPolicy {
            if (tokenTtl != null && maxLifetime != null && maxLifetime.compareTo(tokenTtl) < 0) {
                throw new IllegalStateException("max-lifetime must be greater than or equal to token-ttl");
            }
        }
    }
}