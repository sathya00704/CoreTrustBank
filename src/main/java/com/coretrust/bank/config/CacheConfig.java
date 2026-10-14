package com.coretrust.bank.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String SECURITY_USERS = "security-users";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))      // safety net: even a missed eviction heals in a minute
                .disableCachingNullValues()
                .prefixCacheNameWith("cache:");
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(defaults).build();
    }

    /** Fail open: if Redis misbehaves, log it and behave as if the cache were empty. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache get failed ({}), falling back to the database: {}", cache.getName(), ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache put failed ({}): {}", cache.getName(), ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.error("Cache evict failed ({}) for key {}: entry may be stale until its TTL", cache.getName(), key, ex);
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.error("Cache clear failed ({})", cache.getName(), ex);
            }
        };
    }
}