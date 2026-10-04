package com.coretrust.bank.notification.listener;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.transaction.event.AccountAlertEvent;
import com.coretrust.bank.transaction.event.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountAlertEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountAlert(AccountAlertEvent event) {
        String direction = event.type() == AlertType.DEBIT ? "debited from" : "credited to";
        String account = MaskingUtils.maskAccountNumber(event.accountNumber());
        try {
            emailService.send(event.customerEmail(),
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
        } catch (BusinessException ex) {
            log.warn("Account alert email could not be delivered (reference {})", event.reference());
        }
    }
}