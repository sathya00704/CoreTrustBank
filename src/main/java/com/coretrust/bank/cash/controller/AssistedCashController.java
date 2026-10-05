package com.coretrust.bank.cash.controller;

import com.coretrust.bank.cash.dto.CashTransactionResponse;
import com.coretrust.bank.cash.dto.DepositRequest;
import com.coretrust.bank.cash.service.CashService;
import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.cash.dto.WithdrawalOtpRequest;
import com.coretrust.bank.cash.dto.WithdrawalRequest;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assisted cash", description = "Branch cash deposits (and withdrawals in 5B) by staff")
@RestController
@RequestMapping("/api/v1/assisted")
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
@RequiredArgsConstructor
public class AssistedCashController {

    private final CashService cashService;

    @PostMapping("/deposits")
    public ResponseEntity<CashTransactionResponse> deposit(@AuthenticationPrincipal SecurityUser staff,
                                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                           @Valid @RequestBody DepositRequest request) {
        IdempotentResult<CashTransactionResponse> result = cashService.deposit(staff, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/withdrawals/otp")
    public OtpChallengeResponse requestWithdrawalOtp(@AuthenticationPrincipal SecurityUser staff,
                                                     @Valid @RequestBody WithdrawalOtpRequest request) {
        return cashService.requestWithdrawalOtp(staff, request);
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<CashTransactionResponse> withdraw(@AuthenticationPrincipal SecurityUser staff,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            @Valid @RequestBody WithdrawalRequest request) {
        IdempotentResult<CashTransactionResponse> result = cashService.withdraw(staff, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.response());
    }
}