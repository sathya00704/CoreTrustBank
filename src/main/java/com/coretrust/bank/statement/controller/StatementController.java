package com.coretrust.bank.statement.controller;

import com.coretrust.bank.common.dto.MessageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.statement.dto.StatementEmailRequest;
import com.coretrust.bank.statement.dto.StatementFile;
import com.coretrust.bank.statement.dto.StatementFormat;
import com.coretrust.bank.statement.service.StatementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Statements", description = "Account statements as PDF or CSV")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;

    @GetMapping("/accounts/{accountId}/statement")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "PDF") StatementFormat format) {

        StatementFile file = statementService.download(principal, accountId, fromDate, toDate, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/assisted/statements/email")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public MessageResponse emailToOwner(@AuthenticationPrincipal SecurityUser staff,
                                        @Valid @RequestBody StatementEmailRequest request) {
        String sentTo = statementService.emailToOwner(staff, request);
        return MessageResponse.of("Statement sent to the account owner's registered email (%s).".formatted(sentTo));
    }
}