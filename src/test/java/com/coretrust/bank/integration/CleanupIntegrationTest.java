package com.coretrust.bank.integration;

import com.coretrust.bank.auth.entity.RefreshToken;
import com.coretrust.bank.auth.repository.RefreshTokenRepository;
import com.coretrust.bank.maintenance.CleanupService;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupIntegrationTest extends IntegrationTest {

    @Autowired
    private CleanupService cleanupService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void deletesOnlyRowsPastTheirRetention() {
        User user = createUser("Sathya", Role.CUSTOMER);
        refreshTokenRepository.save(token(user, "old", Instant.now().minus(Duration.ofDays(10))));
        refreshTokenRepository.save(token(user, "recent", Instant.now().minus(Duration.ofDays(2))));
        refreshTokenRepository.save(token(user, "current", Instant.now().plus(Duration.ofMinutes(20))));

        Map<String, Integer> deleted = cleanupService.runCleanup();

        assertThat(deleted.get("refresh_tokens")).isEqualTo(1);
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .containsExactlyInAnyOrder("recent", "current");
    }

    private static RefreshToken token(User user, String hash, Instant sessionEnd) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setSessionId("session");
        token.setTokenHash(hash);
        token.setExpiresAt(sessionEnd);
        token.setSessionExpiresAt(sessionEnd);
        token.setExtended(false);
        return token;
    }
}