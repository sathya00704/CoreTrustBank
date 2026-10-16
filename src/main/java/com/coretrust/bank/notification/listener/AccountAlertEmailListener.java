package com.coretrust.bank.notification.listener;

import com.coretrust.bank.notification.service.AccountAlertEmails;
import com.coretrust.bank.transaction.entity.TransactionType;
import com.coretrust.bank.transaction.event.AccountAlertEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AccountAlertEmailListener {

    private final AccountAlertEmails accountAlertEmails;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountAlert(AccountAlertEvent event) {
        if (event.transactionType() == TransactionType.TRANSFER) {
            return;   // transfer emails come from TransferNotificationConsumer (Kafka)
        }
        accountAlertEmails.send(event);
    }
}