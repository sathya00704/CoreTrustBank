package com.coretrust.bank.notification.listener;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.user.event.PhoneUpdatedEvent;
import com.coretrust.bank.user.event.UserLockedEvent;
import com.coretrust.bank.user.event.UserUnlockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserLocked(UserLockedEvent event) {
        send(event.email(), "Your CoreTrust profile has been locked", """
                Dear %s,

                Your CoreTrust profile has been locked by CoreTrust.

                Reason: %s

                You cannot log in while your profile is locked. Your accounts and money are safe.
                Please contact CoreTrust Support to have it unlocked.
                """.formatted(event.name(), event.reason()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserUnlocked(UserUnlockedEvent event) {
        send(event.email(), "Your CoreTrust profile has been unlocked", """
                Dear %s,

                Your CoreTrust profile has been unlocked. You can log in again.

                If you did not expect this, contact CoreTrust immediately.
                """.formatted(event.name()));
    }

    private void send(String to, String subject, String body) {
        try {
            emailService.send(to, subject, body);
        } catch (BusinessException ex) {
            log.warn("User status email could not be delivered (subject: {})", subject);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPhoneUpdated(PhoneUpdatedEvent event) {
        send(event.email(), "Your CoreTrust phone number has been changed", """
                Dear %s,

                The phone number on your CoreTrust profile was changed by %s.

                  Old number: %s
                  New number: %s

                If you did not request this change, contact CoreTrust immediately.
                """.formatted(event.name(), event.updatedBy(), event.oldPhoneMasked(), event.newPhoneMasked()));
    }
}