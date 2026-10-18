package com.coretrust.bank.web;

import com.coretrust.bank.account.controller.AccountController;
import com.coretrust.bank.account.dto.AccountResponse;
import com.coretrust.bank.account.entity.AccountStatus;
import com.coretrust.bank.account.entity.AccountType;
import com.coretrust.bank.account.service.AccountService;
import com.coretrust.bank.config.SecurityConfig;
import com.coretrust.bank.security.RestAccessDeniedHandler;
import com.coretrust.bank.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AccountControllerWebTest extends WebSliceTest {

    @MockitoBean
    private AccountService accountService;

    private static final String SAVINGS = """
            {"accountType": "SAVINGS"}
            """;
    private static final String FREEZE_REASON = """
            {"reason": "Customer reported a lost phone"}
            """;

    // ---------- who may do what ----------

    @Test
    void customer_canOpenAnAccount() throws Exception {
        when(accountService.createAccount(any(), any())).thenReturn(sampleAccount());

        mockMvc.perform(post("/api/v1/accounts").with(as(customer(10)))
                        .contentType(MediaType.APPLICATION_JSON).content(SAVINGS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("100100000001"));
    }

    @Test
    void admin_cannotOpenAccountsForThemselves() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(as(admin(1)))
                        .contentType(MediaType.APPLICATION_JSON).content(SAVINGS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(accountService);   // rejected before reaching the service
    }

    @Test
    void customerSupportAndAdmin_canAllFreeze() throws Exception {
        when(accountService.freeze(any(), any(), any(), any())).thenReturn(sampleAccount());

        for (var user : new com.coretrust.bank.security.SecurityUser[]{customer(10), support(2), admin(1)}) {
            mockMvc.perform(post("/api/v1/accounts/5/freeze").with(as(user))
                            .contentType(MediaType.APPLICATION_JSON).content(FREEZE_REASON))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void selfServiceUnfreeze_isForCustomersOnly() throws Exception {
        String otp = """
                {"otpId": 1, "otpCode": "123456"}
                """;
        for (var staff : new com.coretrust.bank.security.SecurityUser[]{support(2), admin(1)}) {
            mockMvc.perform(post("/api/v1/accounts/5/unfreeze").with(as(staff))
                            .contentType(MediaType.APPLICATION_JSON).content(otp))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------- error format ----------

    @Test
    void missingAccountType_returnsFieldErrorsInOurFormat() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(as(customer(10)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.accountType").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidJson_returnsMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(as(customer(10)))
                        .contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownEnumValue_listsTheAllowedValues() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").param("status", "BANANA").with(as(admin(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Allowed values: ACTIVE, FROZEN, CLOSED")));
    }

    @Test
    void nonNumericId_returnsAReadableError() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/abc").with(as(customer(10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("must be a number")));
    }

    private static AccountResponse sampleAccount() {
        return new AccountResponse(5L, "100100000001", 10L, AccountType.SAVINGS, new BigDecimal("0.00"),
                "INR", AccountStatus.ACTIVE, null, Instant.now());
    }
}