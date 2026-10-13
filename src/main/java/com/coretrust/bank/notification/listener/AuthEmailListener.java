package com.coretrust.bank.notification.listener;

import com.coretrust.bank.auth.event.PasswordResetCompletedEvent;
import com.coretrust.bank.auth.event.PasswordResetRequestedEvent;
import com.coretrust.bank.caseupdate.event.CaseUpdateEvent;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.notification.service.EmailDispatcher;
import com.coretrust.bank.user.event.EmailChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuthEmailListener {

    private final EmailDispatcher emailDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent e) {
        emailDispatcher.send(e.email(), "Reset your CoreTrust password", """
                Dear %s,

                We received a request to reset your CoreTrust password. Choose a new one here:

                %s

                This link is valid for %d minutes and can be used once.

                If you didn't ask for this, ignore this email: your password stays unchanged.
                """.formatted(e.name(), e.resetLink(), e.validMinutes()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetCompleted(PasswordResetCompletedEvent e) {
        emailDispatcher.send(e.email(), "Your CoreTrust password was reset", """
                Dear %s,

                Your CoreTrust password was just reset, and you were logged out on all devices.

                If you did not do this, contact CoreTrust immediately.
                """.formatted(e.name()));
    }

    /** Sent to the OLD address: that's where the real owner will notice an unwanted change. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailChanged(EmailChangedEvent e) {
        emailDispatcher.send(e.oldEmail(), "Your CoreTrust email address was changed", """
                Dear %s,

                The login email of your CoreTrust profile was changed to %s.
                This address will no longer receive CoreTrust messages.

                If you did not make this change, contact CoreTrust immediately.
                """.formatted(e.name(), MaskingUtils.maskEmail(e.newEmail())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCaseUpdate(CaseUpdateEvent e) {
        emailDispatcher.send(e.customerEmail(), e.title(), """
                Dear %s,

                Update: %s

                You can also see this update in the CoreTrust app.
                """.formatted(e.customerName(), e.message()));
    }
}