package com.coretrust.bank.notification.service;

import com.coretrust.bank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Background email with retries, for notifications. OTPs and setup links still use EmailService directly. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDispatcher {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;

    private final EmailService emailService;

    @Async("emailExecutor")
    public void send(String to, String subject, String body) {
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                emailService.send(to, subject, body);
                return;
            } catch (BusinessException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Email '{}' not delivered after {} attempts; giving up", subject, MAX_ATTEMPTS);
                    return;
                }
                log.warn("Email '{}' attempt {} failed; retrying in {} ms", subject, attempt, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();   // shutting down: stop retrying
                    return;
                }
                backoff *= 2;   // exponential backoff: 2s, then 4s
            }
        }
    }
}