package com.coretrust.bank.integration;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.entity.TransactionType;
import com.coretrust.bank.transfer.dto.TransferRequest;
import com.coretrust.bank.transfer.dto.TransferResponse;
import com.coretrust.bank.transfer.service.TransferService;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TransferConcurrencyIntegrationTest extends IntegrationTest {

    @Autowired
    private TransferService transferService;

    private SecurityUser sathya;
    private Account sathyaAccount;
    private Account ramAccount;

    @BeforeEach
    void setUp() {
        SecurityUser staff = SecurityUser.from(createUser("Teller One", Role.SUPPORT));
        User sathyaUser = createUser("Sathya", Role.CUSTOMER);
        User ramUser = createUser("Ram Kumar", Role.CUSTOMER);

        sathyaAccount = openAccount(sathyaUser, "100100000001");
        ramAccount = openAccount(ramUser, "100100000002");
        addBeneficiary(sathyaUser, ramAccount);
        deposit(staff, sathyaAccount, "10000");

        sathya = SecurityUser.from(sathyaUser);
    }

    @Test
    void tenIdenticalRequestsAtOnce_moveTheMoneyExactlyOnce() throws Exception {
        TransferRequest request = new TransferRequest(sathyaAccount.getId(), ramAccount.getAccountNumber(),
                new BigDecimal("2500"), "Rent");

        List<Outcome<IdempotentResult<TransferResponse>>> outcomes =
                runConcurrently(10, i -> transferService.transfer(sathya, "same-key-0001", request));

        // Every caller gets a successful answer...
        assertThat(outcomes).allMatch(Outcome::ok);
        // ...and it's the SAME transfer for all of them
        Set<Long> transferIds = outcomes.stream().map(o -> o.value().response().id()).collect(Collectors.toSet());
        assertThat(transferIds).hasSize(1);
        // One request did the work; the other nine were replays
        assertThat(outcomes.stream().filter(o -> !o.value().replayed())).hasSize(1);

        assertThat(balanceOf(sathyaAccount)).isEqualByComparingTo("7500.00");
        assertThat(balanceOf(ramAccount)).isEqualByComparingTo("2500.00");
        assertThat(countTransfers()).isEqualTo(1);
    }

    @Test
    void concurrentDifferentTransfersFromOneAccount_neverLoseAnUpdate() throws Exception {
        List<Outcome<IdempotentResult<TransferResponse>>> outcomes = runConcurrently(10, i ->
                transferService.transfer(sathya, "transfer-key-" + i, new TransferRequest(sathyaAccount.getId(),
                        ramAccount.getAccountNumber(), new BigDecimal("1000"), null)));

        long succeeded = outcomes.stream().filter(Outcome::ok).count();

        // The only acceptable failure is an optimistic-lock conflict that survived the retries
        assertThat(outcomes).allMatch(o -> o.ok() || o.error() instanceof OptimisticLockingFailureException);
        assertThat(succeeded).isPositive();

        // Whatever succeeded is reflected EXACTLY: no lost update, no double debit
        BigDecimal moved = new BigDecimal("1000").multiply(BigDecimal.valueOf(succeeded));
        assertThat(balanceOf(sathyaAccount)).isEqualByComparingTo(new BigDecimal("10000").subtract(moved));
        assertThat(balanceOf(ramAccount)).isEqualByComparingTo(moved);
        assertThat(countTransfers()).isEqualTo(succeeded);
    }

    private long countTransfers() {
        return transactionRepository.findAll().stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER)
                .count();
    }
}