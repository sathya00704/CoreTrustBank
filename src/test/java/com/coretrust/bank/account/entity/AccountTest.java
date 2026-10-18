package com.coretrust.bank.account.entity;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.user.entity.Role;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static Account accountWithBalance(String balance) {
        Account account = new Account();
        account.setAccountNumber("100112345678");
        account.credit(new BigDecimal(balance));
        return account;
    }

    private static void assertFailsWith(ErrorCode code, ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(code);
    }

    @Test
    void debit_reducesBalance() {
        Account account = accountWithBalance("1000.00");

        account.debit(new BigDecimal("250.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("749.50");
    }

    @Test
    void debit_moreThanBalance_isRejected_andBalanceUnchanged() {
        Account account = accountWithBalance("100.00");

        assertFailsWith(ErrorCode.INSUFFICIENT_FUNDS, () -> account.debit(new BigDecimal("100.01")));
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void frozenAccount_cannotSend_butCanReceive() {
        Account account = accountWithBalance("500.00");
        account.freeze(Role.CUSTOMER);

        assertFailsWith(ErrorCode.INVALID_ACCOUNT_STATE, () -> account.debit(new BigDecimal("10.00")));

        account.credit(new BigDecimal("50.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("550.00");
    }

    @Test
    void freeze_recordsWhoFroze_andUnfreezeClearsIt() {
        Account account = accountWithBalance("0.00");

        account.freeze(Role.SUPPORT);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
        assertThat(account.getFrozenByRole()).isEqualTo(Role.SUPPORT);

        account.unfreeze();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getFrozenByRole()).isNull();
    }

    @Test
    void freezingTwice_isRejected() {
        Account account = accountWithBalance("0.00");
        account.freeze(Role.ADMIN);

        assertFailsWith(ErrorCode.INVALID_ACCOUNT_STATE, () -> account.freeze(Role.ADMIN));
    }

    @Test
    void close_requiresZeroBalance() {
        Account account = accountWithBalance("0.01");

        assertFailsWith(ErrorCode.INVALID_ACCOUNT_STATE, account::close);
    }

    @Test
    void close_acceptsZeroRegardlessOfScale() {
        Account account = accountWithBalance("5.00");
        account.debit(new BigDecimal("5"));      // balance is now 0.00, not 0

        account.close();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(account.getClosedAt()).isNotNull();
    }

    @Test
    void closedAccount_cannotReceive() {
        Account account = accountWithBalance("0.00");
        account.close();

        assertFailsWith(ErrorCode.INVALID_ACCOUNT_STATE, () -> account.credit(new BigDecimal("1.00")));
    }

    @Test
    void correctionDebit_worksOnFrozenAccount_butNeverGoesNegative() {
        Account account = accountWithBalance("300.00");
        account.freeze(Role.ADMIN);

        account.debitForCorrection(new BigDecimal("200.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");

        assertFailsWith(ErrorCode.INSUFFICIENT_FUNDS, () -> account.debitForCorrection(new BigDecimal("100.01")));
    }
}