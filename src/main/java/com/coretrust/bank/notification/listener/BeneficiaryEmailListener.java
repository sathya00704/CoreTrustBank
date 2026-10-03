package com.coretrust.bank.notification.listener;

import com.coretrust.bank.beneficiary.event.BeneficiaryAddedEvent;
import com.coretrust.bank.beneficiary.event.BeneficiaryRemovedEvent;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeneficiaryEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBeneficiaryAdded(BeneficiaryAddedEvent event) {
        try {
            emailService.send(event.customerEmail(), "New beneficiary added to your CoreTrust profile", """
                    Dear %s,

                    A new beneficiary was added to your profile by %s:

                      Name:    %s
                      Account: %s
                      Bank:    %s

                    You can now send money to this beneficiary.

                    If you did not add this beneficiary, remove it immediately and contact CoreTrust.
                    """.formatted(event.customerName(), event.addedBy(), event.beneficiaryName(),
                    MaskingUtils.maskAccountNumber(event.accountNumber()), event.bankName()));
        } catch (BusinessException ex) {
            log.warn("Beneficiary-added email could not be delivered");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBeneficiaryRemoved(BeneficiaryRemovedEvent event) {
        try {
            emailService.send(event.customerEmail(), "Beneficiary removed from your CoreTrust profile", """
                    Dear %s,

                    A beneficiary was removed from your profile by %s:

                      Name:    %s
                      Account: %s

                    You can no longer send money to this beneficiary unless you add it again.

                    If you did not expect this, contact CoreTrust.
                    """.formatted(event.customerName(), event.removedBy(), event.beneficiaryName(),
                    MaskingUtils.maskAccountNumber(event.accountNumber())));
        } catch (BusinessException ex) {
            log.warn("Beneficiary-removed email could not be delivered");
        }
    }
}