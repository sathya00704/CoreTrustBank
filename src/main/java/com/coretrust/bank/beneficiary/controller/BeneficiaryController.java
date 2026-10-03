package com.coretrust.bank.beneficiary.controller;

import com.coretrust.bank.beneficiary.dto.BeneficiaryRequest;
import com.coretrust.bank.beneficiary.dto.BeneficiaryResponse;
import com.coretrust.bank.beneficiary.entity.BeneficiaryStatus;
import com.coretrust.bank.beneficiary.service.BeneficiaryService;
import com.coretrust.bank.common.dto.MessageResponse;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.beneficiary.dto.BeneficiaryLookupResponse;
import jakarta.validation.constraints.Pattern;
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

@Tag(name = "Beneficiaries", description = "Saved payees for transfers")
@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public BeneficiaryResponse add(@AuthenticationPrincipal SecurityUser principal,
                                   @Valid @RequestBody BeneficiaryRequest request) {
        return beneficiaryService.addBeneficiary(principal, request);
    }

    /** userId and status apply to staff only; customers always see their own active beneficiaries. */
    @GetMapping
    public PageResponse<BeneficiaryResponse> list(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) BeneficiaryStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "beneficiaryName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return beneficiaryService.list(principal, userId, status, pageable);
    }

    @GetMapping("/{id}")
    public BeneficiaryResponse get(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        return beneficiaryService.get(principal, id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public MessageResponse remove(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        beneficiaryService.removeBeneficiary(principal, id);
        return MessageResponse.of("Beneficiary removed.");
    }

    /** Autofill before adding: bank name and masked holder name for CoreTrust accounts. */
    @GetMapping("/lookup")
    public BeneficiaryLookupResponse lookup(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam @Pattern(regexp = "(?i)^[A-Z]{4}0[A-Z0-9]{6}$", message = "must be a valid IFSC code")
            String ifscCode,
            @RequestParam @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
            String accountNumber) {
        return beneficiaryService.lookup(principal, ifscCode, accountNumber);
    }
}