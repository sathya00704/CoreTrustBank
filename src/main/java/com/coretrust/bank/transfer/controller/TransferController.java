package com.coretrust.bank.transfer.controller;

import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transfer.dto.TransferRequest;
import com.coretrust.bank.transfer.dto.TransferResponse;
import com.coretrust.bank.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transfers", description = "Idempotent money transfers between CoreTrust accounts")
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransferResponse> transfer(@AuthenticationPrincipal SecurityUser principal,
                                                     @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                                     @Valid @RequestBody TransferRequest request) {
        IdempotentResult<TransferResponse> result = transferService.transfer(principal, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @GetMapping("/{id}")
    public TransferResponse getTransfer(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        return transferService.getTransfer(principal, id);
    }
}