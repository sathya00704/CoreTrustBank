package com.coretrust.bank.notification.listener;

import com.coretrust.bank.account.event.AccountFrozenEvent;
import com.coretrust.bank.account.event.AccountUnfrozenEvent;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEmailListener {

    private final EmailService emailService;

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
                """.formatted(event.customerName(), mask(event.accountNumber()), frozenBy, event.reason(), nextStep));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountUnfrozen(AccountUnfrozenEvent event) {
        send(event.customerEmail(), "Your CoreTrust account has been unfrozen", """
                Dear %s,

                Your account %s has been unfrozen by %s.

                Reason: %s

                The account is active again and can be used normally.

                For further assistance, contact CoreTrust immediately.
                """.formatted(event.customerName(), mask(event.accountNumber()), event.unfrozenBy(), event.reason()));
    }

    /** The status change is already committed; a failed email must not affect it. */
    private void send(String to, String subject, String body) {
        try {
            emailService.send(to, subject, body);
        } catch (BusinessException ex) {
            log.warn("Account status email could not be delivered (subject: {})", subject);
        }
    }

    /** 100107389094 → XXXXXXXX9094 */
    private static String mask(String accountNumber) {
        int visible = 4;
        return "X".repeat(accountNumber.length() - visible) + accountNumber.substring(accountNumber.length() - visible);
    }
}