package com.coretrust.bank.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.rate-limits")
public record RateLimitProperties(Map<String, @Valid Policy> policies) {

    public Policy policy(String name) {
        Policy policy = policies == null ? null : policies.get(name);
        if (policy == null) {
            throw new IllegalStateException("Missing rate limit policy: app.rate-limits.policies." + name);
        }
        return policy;
    }

    public record Policy(@Min(1) int limit, @NotNull Duration window) {
    }
}