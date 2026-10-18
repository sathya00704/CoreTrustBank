package com.coretrust.bank.statement.service;

import com.coretrust.bank.statement.dto.Statement;
import com.coretrust.bank.statement.dto.StatementLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementCsvRendererTest {

    @Test
    void formulasInDescriptions_areNeutralised() {
        StatementLine malicious = new StatementLine(ZonedDateTime.now(ZoneId.of("Asia/Kolkata")), "TXN1",
                "=HYPERLINK(\"http://evil.example\",\"Click\")", null, new BigDecimal("10.00"), new BigDecimal("10.00"));
        Statement statement = new Statement("CoreTrust Bank", "Sathya", "100100000001", "SAVINGS", "INR",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16), ZonedDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"), new BigDecimal("10.00"), List.of(malicious));

        String csv = new String(new StatementCsvRenderer().render(statement), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"'=HYPERLINK(");     // prefixed with ' so Excel treats it as text
        assertThat(csv).doesNotContain(",\"=HYPERLINK");
    }
}