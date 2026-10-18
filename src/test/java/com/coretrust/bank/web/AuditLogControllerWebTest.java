package com.coretrust.bank.web;

import com.coretrust.bank.audit.controller.AuditLogController;
import com.coretrust.bank.audit.service.AuditLogQueryService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.config.SecurityConfig;
import com.coretrust.bank.security.RestAccessDeniedHandler;
import com.coretrust.bank.security.RestAuthenticationEntryPoint;
import com.coretrust.bank.user.entity.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditLogController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuditLogControllerWebTest extends WebSliceTest {

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditLogQueryService.search(any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0, true));
    }

    @Test
    void plainSupport_cannotViewAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").with(as(support(2))))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportWithViewAuditLogs_can() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").with(as(support(3, Permission.VIEW_AUDIT_LOGS))))
                .andExpect(status().isOk());
    }

    @Test
    void supportWithADifferentPermission_still_cannot() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").with(as(support(4, Permission.MANAGE_USERS))))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canWithoutAnyGrant() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").with(as(admin(1))))
                .andExpect(status().isOk());
    }

    @Test
    void customer_cannot() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").with(as(customer(10))))
                .andExpect(status().isForbidden());
    }
}