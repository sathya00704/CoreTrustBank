package com.coretrust.bank.security;

import com.coretrust.bank.config.CacheConfig;
import com.coretrust.bank.user.entity.User;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Removes a user's cached security details whenever the user row changes,
 * so lock, logout, permission and session changes take effect immediately.
 */
@Component
@RequiredArgsConstructor
public class SecurityUserCacheEvictor {

    private final CacheManager cacheManager;

    /** JPA callback: Hibernate calls this after it writes any change to a User. */
    @PostUpdate
    @PostRemove
    public void onUserChanged(User user) {
        evictAfterCommit(user.getId());
    }

    /**
     * Evict AFTER commit: evicting earlier would let a concurrent request re-cache the old,
     * still-committed values before our transaction finishes.
     */
    public void evictAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(userId);
                }
            });
        } else {
            evict(userId);
        }
    }

    private void evict(Long userId) {
        Cache cache = cacheManager.getCache(CacheConfig.SECURITY_USERS);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}