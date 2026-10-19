package com.coretrust.bank.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class CleanupJob {

    private final CleanupService cleanupService;

    /** Nightly at 3 AM Indian time (cron and zone from application.properties). */
    @Scheduled(cron = "${app.cleanup.cron}", zone = "${app.timezone}")
    public void nightly() {
        cleanupService.runCleanup();
    }
}