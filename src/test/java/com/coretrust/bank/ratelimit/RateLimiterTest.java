package com.coretrust.bank.ratelimit;

import com.coretrust.bank.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties(
                Map.of("login-ip", new RateLimitProperties.Policy(5, Duration.ofMinutes(15))));
        rateLimiter = new RateLimiter(redis, properties);
    }

    @Test
    void consume_overTheLimit_isRejected_withRetryAfter() {
        doReturn(6L).when(redis).execute(any(RedisScript.class), anyList(), anyString());
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(120_000L);

        assertThatThrownBy(() -> rateLimiter.consume("login-ip", "10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting("retryAfterSeconds").isEqualTo(120L);
    }

    @Test
    void consume_atTheLimit_isAllowed() {
        doReturn(5L).when(redis).execute(any(RedisScript.class), anyList(), anyString());

        assertThatCode(() -> rateLimiter.consume("login-ip", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void whenRedisIsDown_requestsAreAllowed() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(redis).execute(any(RedisScript.class), anyList(), anyString());

        assertThatCode(() -> rateLimiter.consume("login-ip", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void keysAreHashed_soNoPersonalDataReachesRedis() {
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), anyString());

        rateLimiter.consume("login-ip", "Sathya@Test.com");

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), anyString());
        String key = keys.getValue().get(0);
        assertThat(key).startsWith("rl:login-ip:").doesNotContain("sathya");
    }
}