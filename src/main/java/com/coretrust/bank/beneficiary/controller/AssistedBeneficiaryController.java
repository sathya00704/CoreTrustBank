package com.coretrust.bank.beneficiary.controller;

import com.coretrust.bank.beneficiary.dto.AssistedBeneficiaryOtpRequest;
import com.coretrust.bank.beneficiary.dto.AssistedBeneficiaryRequest;
import com.coretrust.bank.beneficiary.dto.BeneficiaryResponse;
import com.coretrust.bank.beneficiary.dto.DeactivateBeneficiaryRequest;
import com.coretrust.bank.beneficiary.service.BeneficiaryService;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.security.SecurityUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assisted beneficiaries", description = "Staff add (with customer OTP) or deactivate beneficiaries")
@RestController
@RequestMapping("/api/v1/assisted/beneficiaries")
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
@RequiredArgsConstructor
public class AssistedBeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @PostMapping("/otp")
    public OtpChallengeResponse requestOtp(@AuthenticationPrincipal SecurityUser staff,
                                           @Valid @RequestBody AssistedBeneficiaryOtpRequest request) {
        return beneficiaryService.requestAssistedOtp(staff, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse add(@AuthenticationPrincipal SecurityUser staff,
                                   @Valid @RequestBody AssistedBeneficiaryRequest request) {
        return beneficiaryService.addAssisted(staff, request);
    }

    @PostMapping("/{id}/deactivate")
    public BeneficiaryResponse deactivate(@AuthenticationPrincipal SecurityUser staff, @PathVariable Long id,
                                          @Valid @RequestBody DeactivateBeneficiaryRequest request) {
        return beneficiaryService.deactivateAssisted(staff, id, request.reason());
    }
}