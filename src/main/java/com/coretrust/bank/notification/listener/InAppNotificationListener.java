package com.coretrust.bank.notification.listener;

import com.coretrust.bank.account.event.AccountClosedEvent;
import com.coretrust.bank.account.event.AccountFrozenEvent;
import com.coretrust.bank.account.event.AccountUnfrozenEvent;
import com.coretrust.bank.beneficiary.event.BeneficiaryAddedEvent;
import com.coretrust.bank.beneficiary.event.BeneficiaryRemovedEvent;
import com.coretrust.bank.notification.entity.NotificationType;
import com.coretrust.bank.notification.service.NotificationService;
import com.coretrust.bank.transaction.event.AccountAlertEvent;
import com.coretrust.bank.transaction.event.AlertType;
import com.coretrust.bank.user.event.PhoneUpdatedEvent;
import com.coretrust.bank.user.event.UserLockedEvent;
import com.coretrust.bank.user.event.UserUnlockedEvent;
import com.coretrust.bank.auth.event.PasswordResetCompletedEvent;
import com.coretrust.bank.caseupdate.event.CaseUpdateEvent;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.user.event.EmailChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.coretrust.bank.common.util.MaskingUtils.maskAccountNumber;

/** BEFORE_COMMIT: runs inside the business transaction, so a notification exists exactly when the change does. */
@Component
@RequiredArgsConstructor
public class InAppNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAccountAlert(AccountAlertEvent e) {
        boolean debit = e.type() == AlertType.DEBIT;
        notificationService.notifyUser(e.customerEmail(), NotificationType.TRANSACTION,
                "%s %,.2f %s".formatted(e.currency(), e.amount(), debit ? "debited" : "credited"),
                "%s %,.2f %s your account %s. %s. Ref: %s. Available balance: %s %,.2f.".formatted(
                        e.currency(), e.amount(), debit ? "debited from" : "credited to",
                        maskAccountNumber(e.accountNumber()), e.details(), e.reference(),
                        e.currency(), e.balanceAfter()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAccountFrozen(AccountFrozenEvent e) {
        notificationService.notifyUser(e.customerEmail(), NotificationType.ACCOUNT, "Account frozen",
                "Your account %s has been frozen. Reason: %s".formatted(maskAccountNumber(e.accountNumber()), e.reason()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAccountUnfrozen(AccountUnfrozenEvent e) {
        notificationService.notifyUser(e.customerEmail(), NotificationType.ACCOUNT, "Account unfrozen",
                "Your account %s is active again. %s".formatted(maskAccountNumber(e.accountNumber()), e.reason()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAccountClosed(AccountClosedEvent e) {
        notificationService.notifyUser(e.customerEmail(), NotificationType.ACCOUNT, "Account closed",
                "Your account %s has been closed as requested.".formatted(maskAccountNumber(e.accountNumber())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBeneficiaryAdded(BeneficiaryAddedEvent e) {
        notificationService.notifyUser(e.customerEmail(), NotificationType.SECURITY, "New beneficiary added",
                "%s (%s, %s) was added as a beneficiary by %s. If this wasn't you, remove it and contact CoreTrust."
                        .formatted(e.beneficiaryName(), maskAccountNumber(e.accountNumber()), e.bankName(), e.addedBy()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBeneficiaryRemoved(BeneficiaryRemovedEvent e) {
        notificationService.notifyUser(e.customerEmail(), NotificationType.SECURITY, "Beneficiary removed",
                "%s (%s) was removed by %s.".formatted(e.beneficiaryName(),
                        maskAccountNumber(e.accountNumber()), e.removedBy()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUserLocked(UserLockedEvent e) {
        notificationService.notifyUser(e.email(), NotificationType.SECURITY, "Profile locked",
                "Your profile was locked. " + e.reason());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUserUnlocked(UserUnlockedEvent e) {
        notificationService.notifyUser(e.email(), NotificationType.SECURITY, "Profile unlocked",
                "Your profile has been unlocked. If you didn't expect this, contact CoreTrust.");
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPhoneUpdated(PhoneUpdatedEvent e) {
        notificationService.notifyUser(e.email(), NotificationType.SECURITY, "Phone number changed",
                "Your phone number was changed from %s to %s by %s. If this wasn't you, contact CoreTrust."
                        .formatted(e.oldPhoneMasked(), e.newPhoneMasked(), e.updatedBy()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCaseUpdate(CaseUpdateEvent e) {
        notificationService.notifyUser(e.customerEmail(), NotificationType.CASE_UPDATE, e.title(), e.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPasswordReset(PasswordResetCompletedEvent e) {
        notificationService.notifyUser(e.email(), NotificationType.SECURITY, "Password reset",
                "Your password was reset and you were logged out everywhere. If this wasn't you, contact CoreTrust.");
    }

    /** Looked up by the NEW email: in this transaction, the user already has it. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEmailChanged(EmailChangedEvent e) {
        notificationService.notifyUser(e.newEmail(), NotificationType.SECURITY, "Email address changed",
                "Your login email was changed from %s to %s.".formatted(
                        MaskingUtils.maskEmail(e.oldEmail()), MaskingUtils.maskEmail(e.newEmail())));
    }
}