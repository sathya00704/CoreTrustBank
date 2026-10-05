package com.coretrust.bank.transfer.controller;

import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transfer.dto.AssistedTransferOtpRequest;
import com.coretrust.bank.transfer.dto.AssistedTransferRequest;
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

@Tag(name = "Assisted transfers", description = "Staff transfer on a customer's behalf with the customer's OTP")
@RestController
@RequestMapping("/api/v1/assisted/transfers")
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
@RequiredArgsConstructor
public class AssistedTransferController {

    private final TransferService transferService;

    @PostMapping("/otp")
    public OtpChallengeResponse requestOtp(@AuthenticationPrincipal SecurityUser staff,
                                           @Valid @RequestBody AssistedTransferOtpRequest request) {
        return transferService.requestAssistedOtp(staff, request);
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@AuthenticationPrincipal SecurityUser staff,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody AssistedTransferRequest request) {
        IdempotentResult<TransferResponse> result = transferService.assistedTransfer(staff, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.response());
    }
}