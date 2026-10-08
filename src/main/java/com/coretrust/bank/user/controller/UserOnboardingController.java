package com.coretrust.bank.user.controller;

import com.coretrust.bank.common.dto.MessageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.AssistedRegistrationRequest;
import com.coretrust.bank.user.dto.AssistedRegistrationResponse;
import com.coretrust.bank.user.dto.CreateStaffRequest;
import com.coretrust.bank.user.dto.UserAdminResponse;
import com.coretrust.bank.user.service.UserOnboardingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Onboarding", description = "Staff creation, assisted customer registration and setup links")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserOnboardingController {

    private final UserOnboardingService userOnboardingService;

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserAdminResponse createStaff(@AuthenticationPrincipal SecurityUser admin,
                                         @Valid @RequestBody CreateStaffRequest request) {
        return userOnboardingService.createStaff(admin, request);
    }

    @PostMapping("/assisted/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public AssistedRegistrationResponse registerCustomer(@AuthenticationPrincipal SecurityUser staff,
                                                         @Valid @RequestBody AssistedRegistrationRequest request) {
        return userOnboardingService.registerCustomer(staff, request);
    }

    @PostMapping("/users/{id}/resend-setup-link")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public MessageResponse resendSetupLink(@AuthenticationPrincipal SecurityUser staff, @PathVariable Long id) {
        String sentTo = userOnboardingService.resendSetupLink(staff, id);
        return MessageResponse.of("A new setup link has been sent to " + sentTo + ".");
    }
}