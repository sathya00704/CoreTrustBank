package com.coretrust.bank.integration;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transfer.dto.TransferRequest;
import com.coretrust.bank.transfer.service.TransferService;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerConsistencyIntegrationTest extends IntegrationTest {

    @Autowired
    private TransferService transferService;

    @Test
    void afterMixedOperations_everyBalanceMatchesTheLedger_andNoMoneyIsCreatedOrLost() {
        SecurityUser staff = SecurityUser.from(createUser("Teller One", Role.SUPPORT));
        User owner = createUser("Sathya", Role.CUSTOMER);
        SecurityUser sathya = SecurityUser.from(owner);

        // Three accounts of the same customer: own-account transfers need no beneficiary
        Account a = openAccount(owner, "100100000011");
        Account b = openAccount(owner, "100100000012");
        Account c = openAccount(owner, "100100000013");

        deposit(staff, a, "5000");
        deposit(staff, b, "3000");
        transfer(sathya, a, b, "1200");
        transfer(sathya, b, c, "700.50");
        transfer(sathya, c, a, "200.25");
        transfer(sathya, a, c, "50");

        BigDecimal totalDeposited = new BigDecimal("8000");
        BigDecimal totalNow = balanceOf(a).add(balanceOf(b)).add(balanceOf(c));
        assertThat(totalNow).isEqualByComparingTo(totalDeposited);   // transfers only move money around

        for (Account account : List.of(a, b, c)) {
            assertThat(latestLedgerBalance(account))
                    .as("ledger vs balance for %s", account.getAccountNumber())
                    .isEqualByComparingTo(balanceOf(account));
        }
    }

    private void transfer(SecurityUser user, Account from, Account to, String amount) {
        transferService.transfer(user, "t-" + UUID.randomUUID(),
                new TransferRequest(from.getId(), to.getAccountNumber(), new BigDecimal(amount), null));
    }

    /** The balance-after recorded on the account's most recent transaction. */
    private BigDecimal latestLedgerBalance(Account account) {
        return transactionTemplate.execute(status -> {
            Transaction latest = transactionRepository
                    .findLatestBefore(account.getId(), Instant.now().plusSeconds(60), PageRequest.of(0, 1))
                    .getFirst();
            boolean debit = latest.getFromAccount() != null && latest.getFromAccount().getId().equals(account.getId());
            return debit ? latest.getFromBalanceAfter() : latest.getToBalanceAfter();
        });
    }
}