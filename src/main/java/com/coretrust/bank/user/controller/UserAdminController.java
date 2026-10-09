package com.coretrust.bank.user.controller;

import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.UserAdminResponse;
import com.coretrust.bank.user.dto.UserStatusChangeRequest;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User management", description = "Staff lookup of customers and users; lock and unlock")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public PageResponse<UserAdminResponse> list(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return userAdminService.list(principal, role, status, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public UserAdminResponse get(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        return userAdminService.get(principal, id);
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('PERM_MANAGE_USERS')")
    public UserAdminResponse lock(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id,
                                  @Valid @RequestBody UserStatusChangeRequest request) {
        return userAdminService.lock(principal, id, request.reason(), request.customerMessage());
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('PERM_MANAGE_USERS')")
    public UserAdminResponse unlock(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id,
                                    @Valid @RequestBody UserStatusChangeRequest request) {
        return userAdminService.unlock(principal, id, request.reason());
    }
}