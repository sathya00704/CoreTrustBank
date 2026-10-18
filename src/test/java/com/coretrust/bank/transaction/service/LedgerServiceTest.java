package com.coretrust.bank.transaction.service;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.messaging.EventType;
import com.coretrust.bank.messaging.OutboxService;
import com.coretrust.bank.messaging.payload.TransferCompletedPayload;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.entity.TransactionStatus;
import com.coretrust.bank.transaction.entity.TransactionType;
import com.coretrust.bank.transaction.event.AccountAlertEvent;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private LedgerService ledgerService;

    private Account sathya;
    private Account ram;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ledgerService, "maxTransferAmount", new BigDecimal("200000"));
        // save() returns what it was given; lenient because tests that fail early never save
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        sathya = account(1L, "100100000001", user(10L, "Sathya"), "10000.00");
        ram = account(2L, "100100000002", user(20L, "Ram Kumar"), "500.00");
    }

    @Test
    void transfer_movesMoney_recordsBothBalances_alertsBothSides_andWritesOutboxEvent() {
        Transaction txn = ledgerService.transfer(sathya, ram, new BigDecimal("2500"), "Rent", 10L);

        assertThat(sathya.getBalance()).isEqualByComparingTo("7500.00");
        assertThat(ram.getBalance()).isEqualByComparingTo("3000.00");
        assertThat(txn.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(txn.getFromBalanceAfter()).isEqualByComparingTo("7500.00");
        assertThat(txn.getToBalanceAfter()).isEqualByComparingTo("3000.00");
        assertThat(txn.getTransactionReference()).startsWith("TXN");

        verify(eventPublisher, times(2)).publishEvent(any(AccountAlertEvent.class));
        verify(outboxService).record(eq(EventType.TRANSFER_COMPLETED), any(), any(TransferCompletedPayload.class));
    }

    @Test
    void transfer_withInsufficientFunds_changesNothing() {
        assertThatThrownBy(() -> ledgerService.transfer(ram, sathya, new BigDecimal("500.01"), null, 20L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        assertThat(ram.getBalance()).isEqualByComparingTo("500.00");
        assertThat(sathya.getBalance()).isEqualByComparingTo("10000.00");
        verifyNoInteractions(transactionRepository, outboxService, eventPublisher);
    }

    @Test
    void transfer_aboveTheLimit_isRejected() {
        sathya.credit(new BigDecimal("500000"));

        assertThatThrownBy(() -> ledgerService.transfer(sathya, ram, new BigDecimal("200000.01"), null, 10L))
                .extracting("errorCode").isEqualTo(ErrorCode.TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void transfer_toTheSameAccount_isRejected() {
        assertThatThrownBy(() -> ledgerService.transfer(sathya, sathya, new BigDecimal("1"), null, 10L))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRANSFER);
    }

    @Test
    void transfer_toAFrozenAccount_isAllowed() {
        ram.freeze(Role.ADMIN);

        ledgerService.transfer(sathya, ram, new BigDecimal("100"), null, 10L);

        assertThat(ram.getBalance()).isEqualByComparingTo("600.00");
    }

    @Test
    void reverse_movesTheMoneyBack_andMarksTheOriginalReversed() {
        Transaction original = ledgerService.transfer(sathya, ram, new BigDecimal("1000"), null, 10L);

        Transaction reversal = ledgerService.reverse(original, 99L);

        assertThat(sathya.getBalance()).isEqualByComparingTo("10000.00");
        assertThat(ram.getBalance()).isEqualByComparingTo("500.00");
        assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(reversal.getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversal.getOriginalTransaction()).isSameAs(original);
    }

    @Test
    void reversingTwice_isRejected() {
        Transaction original = ledgerService.transfer(sathya, ram, new BigDecimal("1000"), null, 10L);
        ledgerService.reverse(original, 99L);

        assertThatThrownBy(() -> ledgerService.reverse(original, 99L))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST_STATE);
    }

    // ---------- helpers ----------

    private static User user(Long id, String name) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);   // id has no setter: the database assigns it
        user.setName(name);
        user.setEmail(name.split(" ")[0].toLowerCase() + "@test.com");
        return user;
    }

    private static Account account(Long id, String number, User owner, String balance) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setAccountNumber(number);
        account.setUser(owner);
        account.credit(new BigDecimal(balance));
        return account;
    }
}