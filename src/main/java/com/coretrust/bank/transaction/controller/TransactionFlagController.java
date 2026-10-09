package com.coretrust.bank.transaction.controller;

import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.dto.FlagRequest;
import com.coretrust.bank.transaction.dto.TransactionFlagResponse;
import com.coretrust.bank.transaction.service.TransactionFlagService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Suspicious transactions", description = "Admins flag transactions for investigation")
@RestController
@RequestMapping("/api/v1/transactions/{id}")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_FLAG_TRANSACTIONS')")
public class TransactionFlagController {

    private final TransactionFlagService transactionFlagService;

    @PostMapping("/flag")
    public TransactionFlagResponse flag(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                        @Valid @RequestBody FlagRequest request) {
        return transactionFlagService.flag(admin, id, request.reason());
    }

    @PostMapping("/unflag")
    public TransactionFlagResponse unflag(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                          @Valid @RequestBody FlagRequest request) {
        return transactionFlagService.unflag(admin, id, request.reason());
    }
}