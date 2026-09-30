package com.coretrust.bank.user.controller;

import com.coretrust.bank.common.dto.MessageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.UpdateProfileRequest;
import com.coretrust.bank.user.dto.UserResponse;
import com.coretrust.bank.user.service.UserService;
import com.coretrust.bank.user.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users", description = "Profile of the logged-in user")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getMyProfile(@AuthenticationPrincipal SecurityUser principal) {
        return userService.getProfile(principal.id());
    }

    @PatchMapping("/me")
    public UserResponse updateMyProfile(@AuthenticationPrincipal SecurityUser principal,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.id(), request);
    }

    @PatchMapping("/me/password")
    public MessageResponse changeMyPassword(@AuthenticationPrincipal SecurityUser principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.id(), request);
        return MessageResponse.of("Password changed successfully. Please log in again with your new password.");
    }
}