package com.coretrust.bank.statement.service;

import com.coretrust.bank.statement.dto.Statement;
import com.coretrust.bank.statement.dto.StatementLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Component
public class StatementCsvRenderer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] render(Statement s) {
        StringBuilder csv = new StringBuilder("\uFEFF");   // BOM, so Excel opens it as UTF-8
        csv.append(row("Bank", s.bankName()));
        csv.append(row("Account holder", s.accountHolder()));
        csv.append(row("Account number", s.accountNumber()));
        csv.append(row("Period", s.fromDate() + " to " + s.toDate()));
        csv.append(row("Opening balance", plain(s.openingBalance())));
        csv.append("\n");
        csv.append(row("Date", "Reference", "Description", "Debit", "Credit", "Balance"));
        for (StatementLine line : s.lines()) {
            csv.append(row(line.dateTime().format(DATE_TIME), line.reference(), line.narrative(),
                    plain(line.debit()), plain(line.credit()), plain(line.balance())));
        }
        csv.append("\n");
        csv.append(row("Total debits", plain(s.totalDebits())));
        csv.append(row("Total credits", plain(s.totalCredits())));
        csv.append(row("Closing balance", plain(s.closingBalance())));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String row(String... cells) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escape(cells[i]));
        }
        return row.append('\n').toString();
    }

    /** Quote every cell and double inner quotes; prefix formula characters so spreadsheets don't execute them. */
    private static String escape(String value) {
        if (value == null) {
            return "\"\"";
        }
        String safe = value.matches("^[=+\\-@].*") ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String plain(BigDecimal amount) {
        return amount == null ? "" : amount.toPlainString();
    }
}