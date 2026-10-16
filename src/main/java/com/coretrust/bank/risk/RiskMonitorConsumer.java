package com.coretrust.bank.risk;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.messaging.EventParser;
import com.coretrust.bank.messaging.EventType;
import com.coretrust.bank.messaging.IncomingEvent;
import com.coretrust.bank.messaging.ProcessedEventService;
import com.coretrust.bank.messaging.payload.TransferCompletedPayload;
import com.coretrust.bank.messaging.payload.TransferFailedPayload;
import com.coretrust.bank.ratelimit.RateLimitProperties;
import com.coretrust.bank.ratelimit.RateLimiter;
import com.coretrust.bank.transaction.service.TransactionFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** The "risk service": watches transfers and raises flags/alerts for humans to review. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskMonitorConsumer {

    private static final String CONSUMER = "risk-monitor";
    private static final String FAILURE_POLICY = "transfer-failures";

    private final EventParser eventParser;
    private final ProcessedEventService processedEventService;
    private final TransactionFlagService transactionFlagService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final AuditService auditService;

    @Value("${app.risk.high-value-threshold}")
    private BigDecimal highValueThreshold;

    @KafkaListener(topics = EventType.TOPIC_TRANSFERS, groupId = "coretrust-risk")
    @Transactional
    public void onTransferEvent(String message) {
        IncomingEvent event = eventParser.parse(message);
        if (!processedEventService.markProcessed(CONSUMER, event.eventId())) {
            log.info("Duplicate event {} ignored", event.eventId());
            return;
        }

        if (EventType.TRANSFER_COMPLETED.name().equals(event.eventType())) {
            checkHighValue(eventParser.payload(event, TransferCompletedPayload.class));
        } else if (EventType.TRANSFER_FAILED.name().equals(event.eventType())) {
            countFailure(eventParser.payload(event, TransferFailedPayload.class));
        }
    }

    private void checkHighValue(TransferCompletedPayload p) {
        if (p.amount().compareTo(highValueThreshold) >= 0) {
            transactionFlagService.flagBySystem(p.transactionId(),
                    "High-value transfer of INR %,.2f (threshold INR %,.2f)".formatted(p.amount(), highValueThreshold));
        }
    }

    /** Raises one alert per window: exactly when the count reaches the limit, not on every later failure. */
    private void countFailure(TransferFailedPayload p) {
        long count = rateLimiter.recordAndCount(FAILURE_POLICY, p.initiatedById());
        var policy = rateLimitProperties.policy(FAILURE_POLICY);
        if (count == policy.limit()) {
            auditService.record(null, TransactionFlagService.SYSTEM_ACTOR, AuditAction.RISK_ALERT,
                    AuditEntityType.USER, p.initiatedById(),
                    "%d failed transfers within %s by user %d (latest: %s, INR %s)".formatted(count,
                            policy.window(), p.initiatedById(), p.errorCode(), p.amount().toPlainString()));
        }
    }
}