package com.coretrust.bank.transaction.service;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.entity.TransactionStatus;
import com.coretrust.bank.transaction.entity.TransactionType;
import com.coretrust.bank.transaction.event.AccountAlertEvent;
import com.coretrust.bank.transaction.event.AlertType;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

//The only place that moves money. Transfers now, deposits now, and withdrawals and reversals
@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.transfers.max-amount}")
    private BigDecimal maxTransferAmount;

    /** MANDATORY: must run inside the caller's transaction, so balances and records commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction transfer(Account from, Account to, BigDecimal amount, String description, Long initiatedBy) {
        if (from.getId().equals(to.getId())) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Source and destination accounts must be different.");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Cross-currency transfers are not supported.");
        }
        requireValidAmount(amount);
        if (amount.compareTo(maxTransferAmount) > 0) {
            throw new BusinessException(ErrorCode.TRANSFER_LIMIT_EXCEEDED,
                    "A single transfer cannot exceed INR %,.2f.".formatted(maxTransferAmount));
        }

        from.debit(amount);
        to.credit(amount);

        Transaction txn = newTransaction(TransactionType.TRANSFER, amount, from.getCurrency(), description, initiatedBy);
        txn.setFromAccount(from);
        txn.setToAccount(to);
        txn.setFromBalanceAfter(from.getBalance());
        txn.setToBalanceAfter(to.getBalance());
        Transaction saved = transactionRepository.save(txn);

        User sender = from.getUser();
        User recipient = to.getUser();
        alert(sender, from, AlertType.DEBIT, saved, saved.getFromBalanceAfter(),
                "Transferred to %s (%s)".formatted(MaskingUtils.maskAccountNumber(to.getAccountNumber()), recipient.getName()));
        alert(recipient, to, AlertType.CREDIT, saved, saved.getToBalanceAfter(),
                "Received from %s (%s)".formatted(MaskingUtils.maskAccountNumber(from.getAccountNumber()), sender.getName()));
        return saved;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction deposit(Account to, BigDecimal amount, String description, Long initiatedBy) {
        requireValidAmount(amount);
        to.credit(amount);

        Transaction txn = newTransaction(TransactionType.DEPOSIT, amount, to.getCurrency(), description, initiatedBy);
        txn.setToAccount(to);
        txn.setToBalanceAfter(to.getBalance());
        Transaction saved = transactionRepository.save(txn);

        alert(to.getUser(), to, AlertType.CREDIT, saved, saved.getToBalanceAfter(), "Cash deposit at a CoreTrust branch");
        return saved;
    }

    // ---------- helpers ----------

    private static void requireValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Amount can have at most 2 decimal places.");
        }
    }

    private static Transaction newTransaction(TransactionType type, BigDecimal amount, String currency,
                                              String description, Long initiatedBy) {
        Transaction txn = new Transaction();
        txn.setTransactionReference(newReference());
        txn.setType(type);
        txn.setStatus(TransactionStatus.SUCCESS);
        txn.setAmount(amount.setScale(2));
        txn.setCurrency(currency);
        txn.setDescription(description);
        txn.setInitiatedById(initiatedBy);
        return txn;
    }

    /** e.g. TXN2026100347201938: date for readability, random digits for uniqueness (DB constraint as backstop) */
    private static String newReference() {
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "TXN" + date + "%08d".formatted(RANDOM.nextInt(100_000_000));
    }

    private void alert(User owner, Account account, AlertType type, Transaction txn,
                       BigDecimal balanceAfter, String details) {
        eventPublisher.publishEvent(new AccountAlertEvent(owner.getName(), owner.getEmail(),
                account.getAccountNumber(), type, txn.getAmount(), txn.getCurrency(), balanceAfter,
                details, txn.getTransactionReference()));
    }
}