package com.coretrust.bank.notification.service;

import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.transaction.event.AccountAlertEvent;
import com.coretrust.bank.transaction.event.AlertType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountAlertEmails {

    private final EmailDispatcher emailDispatcher;

    public void send(AccountAlertEvent event) {
        String direction = event.type() == AlertType.DEBIT ? "debited from" : "credited to";
        String account = MaskingUtils.maskAccountNumber(event.accountNumber());
        emailDispatcher.send(event.customerEmail(),
                "CoreTrust alert: %s %,.2f %s your account %s".formatted(
                        event.currency(), event.amount(), direction, account),
                """
                Dear %s,

                %s %,.2f has been %s your account %s.

                Details:           %s
                Reference:         %s
                Available balance: %s %,.2f

                If you did not authorise this transaction, contact CoreTrust immediately.
                """.formatted(event.customerName(), event.currency(), event.amount(), direction, account,
                        event.details(), event.reference(), event.currency(), event.balanceAfter()));
    }
}