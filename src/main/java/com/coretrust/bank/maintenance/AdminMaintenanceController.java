package com.coretrust.bank.maintenance;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin: maintenance", description = "Run the cleanup job on demand")
@RestController
@RequestMapping("/api/v1/admin/maintenance")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMaintenanceController {

    private final CleanupService cleanupService;
    private final AuditService auditService;

    @PostMapping("/cleanup")
    public Map<String, Integer> runCleanup() {
        Map<String, Integer> deleted = cleanupService.runCleanup();
        auditService.record(AuditAction.MAINTENANCE_CLEANUP, AuditEntityType.SYSTEM, null,
                "Cleanup run manually: " + deleted);
        return deleted;
    }
}