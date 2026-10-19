package com.coretrust.bank.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Value("${app.cleanup.retention}")
    private Duration retention;

    @Value("${app.cleanup.idempotency-retention}")
    private Duration idempotencyRetention;

    @Value("${app.cleanup.processed-events-retention}")
    private Duration processedEventsRetention;

    /** Returns how many rows were deleted per table. */
    public Map<String, Integer> runCleanup() {
        Instant now = Instant.now(clock);
        Timestamp cutoff = Timestamp.from(now.minus(retention));

        Map<String, Integer> deleted = new LinkedHashMap<>();
        deleted.put("refresh_tokens", deleteInBatches("refresh_tokens", "session_expires_at < ?", cutoff));
        deleted.put("otps", deleteInBatches("otps", "expires_at < ?", cutoff));
        deleted.put("password_setup_tokens", deleteInBatches("password_setup_tokens", "expires_at < ?", cutoff));
        deleted.put("idempotency_records", deleteInBatches("idempotency_records", "created_at < ?",
                Timestamp.from(now.minus(idempotencyRetention))));
        deleted.put("processed_events", deleteInBatches("processed_events", "processed_at < ?",
                Timestamp.from(now.minus(processedEventsRetention))));
        // published_at IS NULL never matches "< ?", so unsent events are always kept
        deleted.put("outbox_events", deleteInBatches("outbox_events", "published_at < ?", cutoff));

        log.info("Cleanup finished: {}", deleted);
        return deleted;
    }

    /**
     * Deletes up to BATCH_SIZE rows per short transaction, until nothing is left.
     * Table and condition are fixed strings from this class, never user input.
     */
    private int deleteInBatches(String table, String condition, Timestamp cutoff) {
        String sql = "DELETE FROM %s WHERE id IN (SELECT id FROM %s WHERE %s LIMIT %d)"
                .formatted(table, table, condition, BATCH_SIZE);
        int total = 0;
        int deleted;
        do {
            Integer count = transactionTemplate.execute(status -> jdbc.update(sql, cutoff));
            deleted = count == null ? 0 : count;
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }
}