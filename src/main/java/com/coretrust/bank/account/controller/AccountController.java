package com.coretrust.bank.account.controller;

import com.coretrust.bank.account.dto.AccountResponse;
import com.coretrust.bank.account.dto.AccountStatusChangeRequest;
import com.coretrust.bank.account.dto.BalanceResponse;
import com.coretrust.bank.account.dto.CreateAccountRequest;
import com.coretrust.bank.account.entity.AccountStatus;
import com.coretrust.bank.account.service.AccountService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.dto.OtpVerificationRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Accounts", description = "Account creation, lookup, balance and admin freeze/unfreeze")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse createAccount(@AuthenticationPrincipal SecurityUser principal,
                                         @Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(principal, request);
    }

    /** userId and status filters apply to staff only. */
    @GetMapping
    public PageResponse<AccountResponse> listAccounts(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AccountStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return accountService.listAccounts(principal, userId, status, pageable);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        return accountService.getAccount(principal, id);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        return accountService.getBalance(principal, id);
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPPORT', 'ADMIN')")
    public AccountResponse freeze(@AuthenticationPrincipal SecurityUser principal,
                                  @PathVariable Long id,
                                  @Valid @RequestBody AccountStatusChangeRequest request) {
        return accountService.freeze(principal, id, request.reason());
    }

    @PostMapping("/{id}/unfreeze/otp")
    @PreAuthorize("hasRole('CUSTOMER')")
    public OtpChallengeResponse requestUnfreezeOtp(@AuthenticationPrincipal SecurityUser principal,
                                                   @PathVariable Long id) {
        return accountService.requestSelfUnfreezeOtp(principal, id);
    }

    @PostMapping("/{id}/unfreeze")
    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse unfreeze(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id,
                                    @Valid @RequestBody OtpVerificationRequest request) {
        return accountService.selfUnfreeze(principal, id, request);
    }
}