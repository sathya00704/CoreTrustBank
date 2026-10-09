package com.coretrust.bank.user.controller;

import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.PhoneUpdateOtpRequest;
import com.coretrust.bank.user.dto.PhoneUpdateRequest;
import com.coretrust.bank.user.dto.UserAdminResponse;
import com.coretrust.bank.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assisted customer profile", description = "Staff update a customer's phone number with the customer's OTP")
@RestController
@RequestMapping("/api/v1/assisted/customers/phone")
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
@RequiredArgsConstructor
public class AssistedCustomerController {

    private final UserService userService;

    @PostMapping("/otp")
    public OtpChallengeResponse requestOtp(@AuthenticationPrincipal SecurityUser staff,
                                           @Valid @RequestBody PhoneUpdateOtpRequest request) {
        return userService.requestPhoneUpdateOtp(staff, request);
    }

    @PostMapping
    public UserAdminResponse updatePhone(@AuthenticationPrincipal SecurityUser staff,
                                         @Valid @RequestBody PhoneUpdateRequest request) {
        return userService.updatePhoneAssisted(staff, request);
    }
}