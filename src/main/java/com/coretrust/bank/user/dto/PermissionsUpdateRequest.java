package com.coretrust.bank.user.dto;

import com.coretrust.bank.user.entity.Permission;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** The complete new set: [] revokes everything. */
public record PermissionsUpdateRequest(@NotNull Set<Permission> permissions) {
}