package com.coretrust.bank.ratelimit;

import com.coretrust.bank.common.exception.RateLimitExceededException;
import com.coretrust.bank.common.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-window rate limiting in Redis. Fails open: if Redis is unreachable, requests are allowed
 * and a warning is logged, so a cache outage never locks every user out of the bank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    /** INCR and set the expiry in ONE atomic step, so a counter can never be left without a TTL. */
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    /** Counts this attempt and rejects it if the limit is exceeded. */
    public void consume(String policyName, Object subject) {
        RateLimitProperties.Policy policy = properties.policy(policyName);
        String key = key(policyName, subject);
        try {
            Long count = increment(key, policy);
            if (count != null && count > policy.limit()) {
                throw new RateLimitExceededException(retryAfterSeconds(key, policy));
            }
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, allowing request (policy {}): {}", policyName, ex.getMessage());
        }
    }

    /** For failure-based limits: rejects if the subject has already reached the limit. Doesn't count. */
    public void requireNotBlocked(String policyName, Object subject) {
        RateLimitProperties.Policy policy = properties.policy(policyName);
        String key = key(policyName, subject);
        try {
            String value = redis.opsForValue().get(key);
            if (value != null && Long.parseLong(value) >= policy.limit()) {
                throw new RateLimitExceededException(retryAfterSeconds(key, policy));
            }
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, allowing request (policy {}): {}", policyName, ex.getMessage());
        }
    }

    /** Counts one event (e.g. a failed login) without rejecting anything. */
    public void record(String policyName, Object subject) {
        RateLimitProperties.Policy policy = properties.policy(policyName);
        try {
            increment(key(policyName, subject), policy);
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, event not counted (policy {}): {}", policyName, ex.getMessage());
        }
    }

    /** Clears a counter, e.g. failed logins after a successful login. */
    public void reset(String policyName, Object subject) {
        try {
            redis.delete(key(policyName, subject));
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, counter not reset (policy {}): {}", policyName, ex.getMessage());
        }
    }

    // ---------- helpers ----------

    private Long increment(String key, RateLimitProperties.Policy policy) {
        return redis.execute(INCREMENT, List.of(key), String.valueOf(policy.window().toMillis()));
    }

    private long retryAfterSeconds(String key, RateLimitProperties.Policy policy) {
        Long ttlMs = redis.getExpire(key, TimeUnit.MILLISECONDS);
        long ms = (ttlMs == null || ttlMs < 0) ? policy.window().toMillis() : ttlMs;
        return Math.max(1, (ms + 999) / 1000);
    }

    /** Subjects (emails, IPs) are hashed so no personal data sits in Redis keys. */
    private static String key(String policyName, Object subject) {
        String normalized = String.valueOf(subject).trim().toLowerCase(Locale.ROOT);
        return "rl:" + policyName + ":" + HashUtils.sha256Hex(normalized);
    }
}