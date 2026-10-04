package com.coretrust.bank.idempotency.service;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.util.HashUtils;
import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.idempotency.entity.IdempotencyRecord;
import com.coretrust.bank.idempotency.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final IdempotencyRecordRepository repository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Runs {@code action} at most once per (user, key). The action returns the id of the transaction it created;
     * {@code toResponse} builds the API response from that id, for first runs and replays alike.
     */
    public <T> IdempotentResult<T> execute(Long userId, String key, String operation, String requestFingerprint,
                                           Supplier<Long> action, Function<Long, T> toResponse) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must be 8-100 characters: letters, digits, '-' or '_'.");
        }
        String requestHash = HashUtils.sha256Hex(operation + "|" + requestFingerprint);

        for (int attempt = 1; ; attempt++) {
            try {
                // One fresh transaction per attempt
                return transactionTemplate.execute(status ->
                        runOnce(userId, key, operation, requestHash, action, toResponse));
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                // Concurrent update of the same account, or a concurrent duplicate of this key:
                // retry; a duplicate will then find the committed record and replay it.
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
            }
        }
    }

    private <T> IdempotentResult<T> runOnce(Long userId, String key, String operation, String requestHash,
                                            Supplier<Long> action, Function<Long, T> toResponse) {
        Optional<IdempotencyRecord> existing = repository.findByUserIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "This Idempotency-Key was already used for a different request.");
            }
            return new IdempotentResult<>(toResponse.apply(record.getTransactionId()), true);
        }

        // Reserve the key first: a concurrent request with the same key blocks on the unique
        // constraint here until this transaction finishes, then fails and is retried as a replay.
        IdempotencyRecord record = repository.saveAndFlush(new IdempotencyRecord(userId, key, operation, requestHash));

        Long transactionId = action.get();
        record.setTransactionId(transactionId);
        return new IdempotentResult<>(toResponse.apply(transactionId), false);
    }
}