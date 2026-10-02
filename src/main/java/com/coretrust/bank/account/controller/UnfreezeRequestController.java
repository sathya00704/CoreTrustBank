package com.coretrust.bank.account.controller;

import com.coretrust.bank.account.dto.CreateUnfreezeRequest;
import com.coretrust.bank.account.dto.ReviewDecisionRequest;
import com.coretrust.bank.account.dto.UnfreezeRequestResponse;
import com.coretrust.bank.account.entity.UnfreezeRequestStatus;
import com.coretrust.bank.account.service.UnfreezeRequestService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.security.SecurityUser;
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

@Tag(name = "Unfreeze requests", description = "Support requests an unfreeze; an admin approves or rejects it")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UnfreezeRequestController {

    private final UnfreezeRequestService unfreezeRequestService;

    @PostMapping("/accounts/{accountId}/unfreeze-requests/otp")
    @PreAuthorize("hasRole('SUPPORT')")
    public OtpChallengeResponse requestOtp(@AuthenticationPrincipal SecurityUser staff,
                                           @PathVariable Long accountId) {
        return unfreezeRequestService.requestOtp(staff, accountId);
    }

    @PostMapping("/accounts/{accountId}/unfreeze-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPPORT')")
    public UnfreezeRequestResponse create(@AuthenticationPrincipal SecurityUser staff,
                                          @PathVariable Long accountId,
                                          @Valid @RequestBody CreateUnfreezeRequest request) {
        return unfreezeRequestService.create(staff, accountId, request);
    }

    @GetMapping("/unfreeze-requests")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public PageResponse<UnfreezeRequestResponse> list(
            @RequestParam(required = false) UnfreezeRequestStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return unfreezeRequestService.list(status, pageable);
    }

    @PostMapping("/unfreeze-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public UnfreezeRequestResponse approve(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                           @Valid @RequestBody ReviewDecisionRequest request) {
        return unfreezeRequestService.approve(admin, id, request.note());
    }

    @PostMapping("/unfreeze-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public UnfreezeRequestResponse reject(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                          @Valid @RequestBody ReviewDecisionRequest request) {
        return unfreezeRequestService.reject(admin, id, request.note());
    }
}