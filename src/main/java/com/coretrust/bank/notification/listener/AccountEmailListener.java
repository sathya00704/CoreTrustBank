package com.coretrust.bank.notification.listener;

import com.coretrust.bank.account.event.AccountClosedEvent;
import com.coretrust.bank.account.event.AccountFrozenEvent;
import com.coretrust.bank.account.event.AccountUnfrozenEvent;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.notification.service.EmailDispatcher;
import com.coretrust.bank.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AccountEmailListener {

    private final EmailDispatcher emailDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountFrozen(AccountFrozenEvent event) {
        String frozenBy = switch (event.frozenBy()) {
            case CUSTOMER -> "you (self-service)";
            case SUPPORT -> "CoreTrust Support, at your request";
            case ADMIN -> "CoreTrust Bank";
        };
        String nextStep = event.frozenBy() == Role.CUSTOMER
                ? "You can unfreeze it yourself in the CoreTrust app (an OTP will be sent to this email), "
                + "or contact CoreTrust Support."
                : "To have it unfrozen, please contact CoreTrust Support.";

        send(event.customerEmail(), "Your CoreTrust account has been frozen", """
                Dear %s,

                Your account %s has been frozen by %s.

                Reason: %s

                While the account is frozen, transfers and withdrawals from it are blocked.
                %s

                For further assistance, contact CoreTrust immediately.
                """.formatted(event.customerName(), MaskingUtils.maskAccountNumber(event.accountNumber()), frozenBy, event.reason(), nextStep));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountUnfrozen(AccountUnfrozenEvent event) {
        send(event.customerEmail(), "Your CoreTrust account has been unfrozen", """
                Dear %s,

                Your account %s has been unfrozen by %s.

                Reason: %s

                The account is active again and can be used normally.

                For further assistance, contact CoreTrust immediately.
                """.formatted(event.customerName(), MaskingUtils.maskAccountNumber(event.accountNumber()), event.unfrozenBy(), event.reason()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountClosed(AccountClosedEvent event) {
        send(event.customerEmail(), "Your CoreTrust account has been closed", """
                Dear %s,

                As requested, your account %s has been closed.

                Reason given: %s

                The account can no longer send or receive money. Its transaction history remains available.

                If you did not request this, contact CoreTrust immediately.
                """.formatted(event.customerName(), MaskingUtils.maskAccountNumber(event.accountNumber()), event.reason()));
    }

    private void send(String to, String subject, String body) {
        emailDispatcher.send(to, subject, body);
    }
}