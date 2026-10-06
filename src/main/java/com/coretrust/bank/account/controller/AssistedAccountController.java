package com.coretrust.bank.account.controller;

import com.coretrust.bank.account.dto.AccountResponse;
import com.coretrust.bank.account.dto.AssistedAccountOtpRequest;
import com.coretrust.bank.account.dto.AssistedAccountRequest;
import com.coretrust.bank.account.service.AccountService;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.account.dto.CloseAccountOtpRequest;
import com.coretrust.bank.account.dto.CloseAccountRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assisted accounts", description = "Staff open accounts for customers with the customer's OTP consent")
@RestController
@RequestMapping("/api/v1/assisted/accounts")
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
@RequiredArgsConstructor
public class AssistedAccountController {

    private final AccountService accountService;

    @PostMapping("/otp")
    public OtpChallengeResponse requestOtp(@AuthenticationPrincipal SecurityUser staff,
                                           @Valid @RequestBody AssistedAccountOtpRequest request) {
        return accountService.requestAssistedAccountOtp(staff, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(@AuthenticationPrincipal SecurityUser staff,
                                       @Valid @RequestBody AssistedAccountRequest request) {
        return accountService.openAssistedAccount(staff, request);
    }

    @PostMapping("/close/otp")
    public OtpChallengeResponse requestCloseOtp(@AuthenticationPrincipal SecurityUser staff,
                                                @Valid @RequestBody CloseAccountOtpRequest request) {
        return accountService.requestCloseOtp(staff, request);
    }

    @PostMapping("/close")
    public AccountResponse closeAccount(@AuthenticationPrincipal SecurityUser staff,
                                        @Valid @RequestBody CloseAccountRequest request) {
        return accountService.closeAssisted(staff, request);
    }
}