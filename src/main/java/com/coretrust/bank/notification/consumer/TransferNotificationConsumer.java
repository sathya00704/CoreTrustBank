package com.coretrust.bank.notification.consumer;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.common.util.AfterCommit;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.messaging.EventParser;
import com.coretrust.bank.messaging.EventType;
import com.coretrust.bank.messaging.IncomingEvent;
import com.coretrust.bank.messaging.ProcessedEventService;
import com.coretrust.bank.messaging.payload.TransferCompletedPayload;
import com.coretrust.bank.notification.service.AccountAlertEmails;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.event.AccountAlertEvent;
import com.coretrust.bank.transaction.event.AlertType;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The "notification service": would be a separate application in a microservice setup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferNotificationConsumer {

    private static final String CONSUMER = "transfer-notifications";

    private final EventParser eventParser;
    private final ProcessedEventService processedEventService;
    private final TransactionRepository transactionRepository;
    private final AccountAlertEmails accountAlertEmails;

    @KafkaListener(topics = EventType.TOPIC_TRANSFERS, groupId = "coretrust-notifications")
    @Transactional
    public void onTransferEvent(String message) {
        IncomingEvent event = eventParser.parse(message);
        if (!EventType.TRANSFER_COMPLETED.name().equals(event.eventType())) {
            return;   // this consumer only cares about completed transfers
        }
        if (!processedEventService.markProcessed(CONSUMER, event.eventId())) {
            log.info("Duplicate event {} ignored", event.eventId());
            return;
        }

        TransferCompletedPayload payload = eventParser.payload(event, TransferCompletedPayload.class);
        // The event carries ids only; fresh names, emails and balances come from the database
        Transaction txn = transactionRepository.findById(payload.transactionId())
                .orElseThrow(() -> new IllegalStateException("Transaction " + payload.transactionId() + " not found"));

        Account from = txn.getFromAccount();
        Account to = txn.getToAccount();

        AccountAlertEvent debit = new AccountAlertEvent(from.getUser().getName(), from.getUser().getEmail(),
                from.getAccountNumber(), AlertType.DEBIT, txn.getAmount(), txn.getCurrency(), txn.getFromBalanceAfter(),
                "Transferred to %s (%s)".formatted(MaskingUtils.maskAccountNumber(to.getAccountNumber()), to.getUser().getName()),
                txn.getTransactionReference(), txn.getType());
        AccountAlertEvent credit = new AccountAlertEvent(to.getUser().getName(), to.getUser().getEmail(),
                to.getAccountNumber(), AlertType.CREDIT, txn.getAmount(), txn.getCurrency(), txn.getToBalanceAfter(),
                "Received from %s (%s)".formatted(MaskingUtils.maskAccountNumber(from.getAccountNumber()), from.getUser().getName()),
                txn.getTransactionReference(), txn.getType());

        // Only once the "processed" marker is committed: a rollback must not have sent emails already
        AfterCommit.run(() -> {
            accountAlertEmails.send(debit);
            accountAlertEmails.send(credit);
        });
    }
}