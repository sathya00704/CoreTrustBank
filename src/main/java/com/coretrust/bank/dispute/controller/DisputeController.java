package com.coretrust.bank.dispute.controller;

import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.dispute.dto.DisputeDecisionRequest;
import com.coretrust.bank.dispute.dto.DisputeResponse;
import com.coretrust.bank.dispute.dto.RaiseDisputeRequest;
import com.coretrust.bank.dispute.entity.DisputeStatus;
import com.coretrust.bank.dispute.service.DisputeService;
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

@Tag(name = "Disputes", description = "Support raises disputes on transactions; admins resolve or reject them")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping("/transactions/{transactionId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPPORT')")
    public DisputeResponse raise(@AuthenticationPrincipal SecurityUser staff, @PathVariable Long transactionId,
                                 @Valid @RequestBody RaiseDisputeRequest request) {
        return disputeService.raise(staff, transactionId, request);
    }

    @GetMapping("/disputes")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public PageResponse<DisputeResponse> list(
            @RequestParam(required = false) DisputeStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return disputeService.list(status, pageable);
    }

    @PostMapping("/disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public DisputeResponse resolve(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                   @Valid @RequestBody DisputeDecisionRequest request) {
        return disputeService.resolve(admin, id, request.note());
    }

    @PostMapping("/disputes/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public DisputeResponse reject(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                  @Valid @RequestBody DisputeDecisionRequest request) {
        return disputeService.reject(admin, id, request.note());
    }
}