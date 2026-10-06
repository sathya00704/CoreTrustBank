package com.coretrust.bank.transaction.controller;

import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.dto.TransactionFilter;
import com.coretrust.bank.transaction.dto.TransactionResponse;
import com.coretrust.bank.transaction.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions", description = "Transaction history with filters, search, sorting and pagination")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @ParameterObject TransactionFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return transactionQueryService.list(principal, filter, pageable);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        return transactionQueryService.get(principal, id);
    }
}