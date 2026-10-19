package com.coretrust.bank.integration;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.messaging.OutboxEvent;
import com.coretrust.bank.messaging.OutboxEventRepository;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.entity.TransactionType;
import com.coretrust.bank.transfer.dto.TransferRequest;
import com.coretrust.bank.transfer.service.TransferService;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxIntegrationTest extends IntegrationTest {

    @Autowired
    private TransferService transferService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private SecurityUser sathya;
    private Account sathyaAccount;
    private Account ramAccount;

    @BeforeEach
    void setUp() {
        SecurityUser staff = SecurityUser.from(createUser("Teller One", Role.SUPPORT));
        User sathyaUser = createUser("Sathya", Role.CUSTOMER);
        sathyaAccount = openAccount(sathyaUser, "100100000021");
        ramAccount = openAccount(createUser("Ram Kumar", Role.CUSTOMER), "100100000022");
        addBeneficiary(sathyaUser, ramAccount);
        deposit(staff, sathyaAccount, "1000");
        sathya = SecurityUser.from(sathyaUser);
    }

    @Test
    void aSuccessfulTransfer_writesExactlyOneTransferCompletedEvent() {
        var result = transferService.transfer(sathya, "outbox-0001",
                new TransferRequest(sathyaAccount.getId(), ramAccount.getAccountNumber(), new BigDecimal("400"), null));

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.getFirst();
        assertThat(event.getEventType()).isEqualTo("TRANSFER_COMPLETED");
        assertThat(event.getEventKey()).isEqualTo(String.valueOf(result.response().id()));
        assertThat(event.getPublishedAt()).isNull();   // waiting for the publisher (switched off in tests)
        assertThat(event.getPayload()).contains("\"eventId\"").contains("\"transactionReference\"");
    }

    @Test
    void aFailedTransfer_writesTransferFailed_butNoTransaction() {
        assertThatThrownBy(() -> transferService.transfer(sathya, "outbox-0002",
                new TransferRequest(sathyaAccount.getId(), ramAccount.getAccountNumber(), new BigDecimal("5000"), null)))
                .isInstanceOf(BusinessException.class);

        // The transfer rolled back...
        assertThat(transactionRepository.findAll()).noneMatch(t -> t.getType() == TransactionType.TRANSFER);
        assertThat(balanceOf(sathyaAccount)).isEqualByComparingTo("1000.00");
        // ...but the record that it was attempted survived (REQUIRES_NEW)
        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getEventType()).isEqualTo("TRANSFER_FAILED");
                    assertThat(e.getPayload()).contains("INSUFFICIENT_FUNDS");
                });
    }
}