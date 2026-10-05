package com.coretrust.bank.transaction.controller;

import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.dto.ReversalRequestBody;
import com.coretrust.bank.transaction.dto.ReversalRequestResponse;
import com.coretrust.bank.transaction.entity.ReversalRequestStatus;
import com.coretrust.bank.transaction.service.ReversalRequestService;
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

@Tag(name = "Reversal requests", description = "Support requests a reversal; an admin approves or rejects it")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReversalRequestController {

    private final ReversalRequestService reversalRequestService;

    @PostMapping("/transactions/{transactionId}/reversal-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPPORT')")
    public ReversalRequestResponse create(@AuthenticationPrincipal SecurityUser staff,
                                          @PathVariable Long transactionId,
                                          @Valid @RequestBody ReversalRequestBody body) {
        return reversalRequestService.create(staff, transactionId, body.text());
    }

    @GetMapping("/reversal-requests")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public PageResponse<ReversalRequestResponse> list(
            @RequestParam(required = false) ReversalRequestStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return reversalRequestService.list(status, pageable);
    }

    @PostMapping("/reversal-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ReversalRequestResponse approve(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                           @Valid @RequestBody ReversalRequestBody body) {
        return reversalRequestService.approve(admin, id, body.text());
    }

    @PostMapping("/reversal-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ReversalRequestResponse reject(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                          @Valid @RequestBody ReversalRequestBody body) {
        return reversalRequestService.reject(admin, id, body.text());
    }
}