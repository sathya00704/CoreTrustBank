package com.coretrust.bank.statement.service;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.statement.dto.Statement;
import com.coretrust.bank.statement.dto.StatementEmailRequest;
import com.coretrust.bank.statement.dto.StatementFile;
import com.coretrust.bank.statement.dto.StatementFormat;
import com.coretrust.bank.statement.dto.StatementLine;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.transaction.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final StatementPdfRenderer pdfRenderer;
    private final StatementCsvRenderer csvRenderer;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.bank.name}")
    private String bankName;

    @Value("${app.timezone}")
    private String timezone;

    @Value("${app.statements.max-range-days}")
    private long maxRangeDays;

    /** Customers: own accounts only (404 otherwise). Staff: any account. */
    @Transactional
    public StatementFile download(SecurityUser viewer, Long accountId, LocalDate fromDate, LocalDate toDate,
                                  StatementFormat format) {
        Account account = (viewer.isStaff()
                ? accountRepository.findById(accountId)
                : accountRepository.findByIdAndUserId(accountId, viewer.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        Statement statement = build(account, fromDate, toDate);
        StatementFile file = render(statement, format);
        auditService.record(AuditAction.STATEMENT_DOWNLOADED, AuditEntityType.ACCOUNT, account.getId(),
                "%s statement for %s, %s to %s".formatted(format, account.getAccountNumber(), fromDate, toDate));
        return file;
    }

    /** Support (item 11): PDF to the owner's registered email only. */
    @Transactional
    public String emailToOwner(SecurityUser staff, StatementEmailRequest request) {
        Account account = accountRepository.findByAccountNumber(request.accountNumber().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No CoreTrust account exists with this account number."));

        Statement statement = build(account, request.fromDate(), request.toDate());
        StatementFile file = render(statement, StatementFormat.PDF);
        String ownerEmail = account.getUser().getEmail();

        emailService.sendWithAttachment(ownerEmail, "Your CoreTrust account statement", """
                Dear %s,

                As requested, your statement for account %s from %s to %s is attached.

                If you did not request this statement, contact CoreTrust.
                """.formatted(statement.accountHolder(), MaskingUtils.maskAccountNumber(account.getAccountNumber()),
                        request.fromDate(), request.toDate()),
                file.fileName(), file.content(), file.contentType());

        auditService.record(AuditAction.STATEMENT_EMAILED, AuditEntityType.ACCOUNT, account.getId(),
                "Statement for %s (%s to %s) emailed to the owner by %s %s".formatted(account.getAccountNumber(),
                        request.fromDate(), request.toDate(), staff.role(), staff.email()));
        return MaskingUtils.maskEmail(ownerEmail);
    }

    // ---------- building ----------

    private Statement build(Account account, LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "fromDate must not be after toDate.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > maxRangeDays) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "A statement can cover at most %d days.".formatted(maxRangeDays));
        }

        ZoneId zone = ZoneId.of(timezone);
        Instant start = fromDate.atStartOfDay(zone).toInstant();
        Instant end = toDate.plusDays(1).atStartOfDay(zone).toInstant();
        Long accountId = account.getId();

        BigDecimal opening = transactionRepository.findLatestBefore(accountId, start, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(t -> balanceAfterFor(t, accountId))
                .orElse(BigDecimal.ZERO.setScale(2));

        List<StatementLine> lines = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO.setScale(2);
        BigDecimal totalCredits = BigDecimal.ZERO.setScale(2);

        for (Transaction t : transactionRepository.findStatementLines(accountId, start, end)) {
            boolean isDebit = t.getFromAccount() != null && t.getFromAccount().getId().equals(accountId);
            if (isDebit) {
                totalDebits = totalDebits.add(t.getAmount());
            } else {
                totalCredits = totalCredits.add(t.getAmount());
            }
            lines.add(new StatementLine(t.getCreatedAt().atZone(zone), t.getTransactionReference(),
                    narrative(t, isDebit),
                    isDebit ? t.getAmount() : null,
                    isDebit ? null : t.getAmount(),
                    balanceAfterFor(t, accountId)));
        }

        BigDecimal closing = lines.isEmpty() ? opening : lines.get(lines.size() - 1).balance();

        return new Statement(bankName, account.getUser().getName(), account.getAccountNumber(),
                account.getAccountType().name(), account.getCurrency(), fromDate, toDate,
                ZonedDateTime.now(zone), opening, totalDebits, totalCredits, closing, lines);
    }

    private StatementFile render(Statement statement, StatementFormat format) {
        String baseName = "statement_%s_%s_%s".formatted(statement.accountNumber(),
                statement.fromDate(), statement.toDate());
        return switch (format) {
            case PDF -> new StatementFile(baseName + ".pdf", "application/pdf", pdfRenderer.render(statement));
            case CSV -> new StatementFile(baseName + ".csv", "text/csv", csvRenderer.render(statement));
        };
    }

    private static BigDecimal balanceAfterFor(Transaction t, Long accountId) {
        return (t.getFromAccount() != null && t.getFromAccount().getId().equals(accountId))
                ? t.getFromBalanceAfter()
                : t.getToBalanceAfter();
    }

    /** What the line means for this account, e.g. "Transfer to XXXXXXXX4865 (Ram Kumar) - Rent". */
    private static String narrative(Transaction t, boolean isDebit) {
        String text = switch (t.getType()) {
            case DEPOSIT -> "Cash deposit";
            case WITHDRAWAL -> "Cash withdrawal";
            case REVERSAL -> "Reversal of " + (t.getOriginalTransaction() == null
                    ? "earlier transaction" : t.getOriginalTransaction().getTransactionReference());
            case TRANSFER -> {
                Account other = isDebit ? t.getToAccount() : t.getFromAccount();
                yield "%s %s (%s)".formatted(isDebit ? "Transfer to" : "Transfer from",
                        MaskingUtils.maskAccountNumber(other.getAccountNumber()), other.getUser().getName());
            }
        };
        return (t.getDescription() == null || t.getType() == TransactionType.REVERSAL)
                ? text : text + " - " + t.getDescription();
    }
}