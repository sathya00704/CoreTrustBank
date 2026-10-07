package com.coretrust.bank.user.controller;

import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.PermissionsUpdateRequest;
import com.coretrust.bank.user.dto.UserAdminResponse;
import com.coretrust.bank.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin: permissions", description = "Grant or revoke extra permissions for SUPPORT users")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final UserAdminService userAdminService;

    @PutMapping("/{id}/permissions")
    public UserAdminResponse updatePermissions(@AuthenticationPrincipal SecurityUser admin, @PathVariable Long id,
                                               @Valid @RequestBody PermissionsUpdateRequest request) {
        return userAdminService.updatePermissions(admin, id, request.permissions());
    }
}