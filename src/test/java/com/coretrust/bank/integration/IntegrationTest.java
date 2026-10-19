package com.coretrust.bank.integration;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.entity.AccountType;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.beneficiary.entity.Beneficiary;
import com.coretrust.bank.beneficiary.repository.BeneficiaryRepository;
import com.coretrust.bank.cash.dto.DepositRequest;
import com.coretrust.bank.cash.service.CashService;
import com.coretrust.bank.notification.service.EmailDispatcher;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.ratelimit.RateLimiter;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Starts the whole application against coretrust_test. Every integration test extends this class
 * and declares no extra mocks, so Spring can reuse ONE application context for all of them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TransactionTemplate transactionTemplate;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected UserRepository userRepository;
    @Autowired protected AccountRepository accountRepository;
    @Autowired protected BeneficiaryRepository beneficiaryRepository;
    @Autowired protected TransactionRepository transactionRepository;
    @Autowired protected CashService cashService;

    @MockitoBean protected EmailService emailService;
    @MockitoBean protected EmailDispatcher emailDispatcher;
    @MockitoBean protected RateLimiter rateLimiter;

    private static int phoneCounter = 0;

    /** Every test starts from an empty database. */
    @BeforeEach
    void cleanDatabase() {
        List<String> tables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = 'public'", String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    // ---------- test data ----------

    protected User createUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@test.com");
        user.setPhone("98%08d".formatted(++phoneCounter));
        user.setPasswordHash(passwordEncoder.encode("Secret@123"));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    protected Account openAccount(User owner, String accountNumber) {
        Account account = new Account();
        account.setUser(owner);
        account.setAccountNumber(accountNumber);
        account.setAccountType(AccountType.SAVINGS);
        return accountRepository.save(account);
    }

    /** Through the real CashService, so the ledger has a matching DEPOSIT entry. */
    protected void deposit(SecurityUser staff, Account account, String amount) {
        cashService.deposit(staff, "dep-" + UUID.randomUUID(),
                new DepositRequest(account.getAccountNumber(), new BigDecimal(amount), null));
    }

    protected void addBeneficiary(User owner, Account target) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setUser(owner);
        beneficiary.setBeneficiaryName(target.getUser().getName());
        beneficiary.setAccountNumber(target.getAccountNumber());
        beneficiary.setBankName("CoreTrust Bank");
        beneficiary.setIfscCode("CTRB0001001");
        beneficiaryRepository.save(beneficiary);
    }

    protected BigDecimal balanceOf(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    // ---------- concurrency ----------

    public record Outcome<T>(T value, Throwable error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** Runs the task on N threads that all start at the same instant, and collects every result or error. */
    protected <T> List<Outcome<T>> runConcurrently(int threads, IntFunction<T> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();              // wait until every thread is ready
                return task.apply(index);
            }));
        }
        ready.await();
        go.countDown();                  // release them all at once

        List<Outcome<T>> outcomes = new ArrayList<>();
        for (Future<T> future : futures) {
            try {
                outcomes.add(new Outcome<>(future.get(30, TimeUnit.SECONDS), null));
            } catch (ExecutionException ex) {
                outcomes.add(new Outcome<>(null, ex.getCause()));
            } catch (Exception ex) {
                outcomes.add(new Outcome<>(null, ex));
            }
        }
        pool.shutdown();
        return outcomes;
    }
}