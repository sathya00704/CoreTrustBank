package com.coretrust.bank.web;

import com.coretrust.bank.config.SecurityConfig;
import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.security.RestAccessDeniedHandler;
import com.coretrust.bank.security.RestAuthenticationEntryPoint;
import com.coretrust.bank.transaction.entity.TransactionStatus;
import com.coretrust.bank.transfer.controller.TransferController;
import com.coretrust.bank.transfer.dto.TransferResponse;
import com.coretrust.bank.transfer.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransferController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TransferControllerWebTest extends WebSliceTest {

    @MockitoBean
    private TransferService transferService;

    private static final String TRANSFER = """
            {"fromAccountId": 1, "toAccountNumber": "100100000002", "amount": 2500, "description": "Rent"}
            """;

    @Test
    void transferWithoutAnIdempotencyKey_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(as(customer(10)))
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSFER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Required header 'Idempotency-Key' is missing."));

        verifyNoInteractions(transferService);
    }

    @Test
    void firstRequest_returns201_andReplayedFalse() throws Exception {
        when(transferService.transfer(any(), eq("txn-0001"), any()))
                .thenReturn(new IdempotentResult<>(sampleTransfer(), false));

        mockMvc.perform(post("/api/v1/transfers").with(as(customer(10))).header("Idempotency-Key", "txn-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSFER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.transactionReference").value("TXN2026101600000001"));
    }

    @Test
    void replayedRequest_returnsTheSameBody_withReplayedTrue() throws Exception {
        when(transferService.transfer(any(), eq("txn-0001"), any()))
                .thenReturn(new IdempotentResult<>(sampleTransfer(), true));

        mockMvc.perform(post("/api/v1/transfers").with(as(customer(10))).header("Idempotency-Key", "txn-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSFER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(55));
    }

    @Test
    void support_cannotUseTheCustomerTransferEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(as(support(2))).header("Idempotency-Key", "txn-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSFER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transferService);
    }

    @Test
    void amountWithThreeDecimals_isRejected() throws Exception {
        String badAmount = TRANSFER.replace("2500", "10.555");

        mockMvc.perform(post("/api/v1/transfers").with(as(customer(10))).header("Idempotency-Key", "txn-0002")
                        .contentType(MediaType.APPLICATION_JSON).content(badAmount))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    private static TransferResponse sampleTransfer() {
        return new TransferResponse(55L, "TXN2026101600000001", "100100000001", "100100000002",
                new BigDecimal("2500.00"), "INR", TransactionStatus.SUCCESS, "Rent", Instant.now());
    }
}