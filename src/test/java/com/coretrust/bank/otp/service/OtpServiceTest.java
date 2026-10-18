package com.coretrust.bank.otp.service;

import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.util.HashUtils;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.otp.config.OtpProperties;
import com.coretrust.bank.otp.entity.Otp;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.repository.OtpRepository;
import com.coretrust.bank.ratelimit.RateLimiter;
import com.coretrust.bank.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private static final String OPERATION = "CASH_WITHDRAWAL|customer=7|account=100100000001|amount=1000";

    @Mock
    private OtpRepository otpRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private RateLimiter rateLimiter;

    // Real BCrypt, but at the lowest cost (4) so tests stay fast
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private OtpService otpService;
    private User customer;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(otpRepository, encoder, emailService, auditService,
                new OtpProperties(Duration.ofMinutes(5), 3), transactionManager, rateLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC));

        customer = new User();
        ReflectionTestUtils.setField(customer, "id", 7L);
        customer.setName("Sathya");
        customer.setEmail("sathya@test.com");
    }

    @Test
    void issue_emailsTheCode_storesOnlyItsHash_andExpiresInFiveMinutes() {
        otpService.issue(customer, OtpPurpose.CASH_WITHDRAWAL, OPERATION, "withdraw cash", 99L);

        verify(rateLimiter).consume("otp-issue", 7L);

        ArgumentCaptor<Otp> savedOtp = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository).save(savedOtp.capture());
        Otp otp = savedOtp.getValue();
        assertThat(otp.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        ArgumentCaptor<String> emailBody = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("sathya@test.com"), anyString(), emailBody.capture());
        String code = extractCode(emailBody.getValue());

        assertThat(otp.getCodeHash()).doesNotContain(code);            // never stored in plain text
        assertThat(encoder.matches(code, otp.getCodeHash())).isTrue();  // but verifiable
        assertThat(emailBody.getValue()).contains("withdraw cash");     // the customer sees what they approve
    }

    @Test
    void verify_withTheRightCodeAndDetails_consumesTheOtp() {
        Otp otp = otp("123456", NOW.plusSeconds(60));
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));

        otpService.verify(1L, 7L, OtpPurpose.CASH_WITHDRAWAL, OPERATION, "123456");

        assertThat(otp.isConsumed()).isTrue();
    }

    @Test
    void verify_withAWrongCode_countsTheAttempt_inItsOwnTransaction() {
        Otp otp = otp("123456", NOW.plusSeconds(60));
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify(1L, 7L, OtpPurpose.CASH_WITHDRAWAL, OPERATION, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 attempt(s) left");

        assertThat(otp.getAttempts()).isEqualTo(1);
        assertThat(otp.isConsumed()).isFalse();
        verify(transactionManager).getTransaction(any());   // the REQUIRES_NEW transaction was opened
    }

    @Test
    void verify_withTheRightCodeButChangedDetails_isRejectedWithAClearMessage() {
        Otp otp = otp("123456", NOW.plusSeconds(60));
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));
        String tamperedOperation = OPERATION.replace("amount=1000", "amount=10000");

        assertThatThrownBy(() -> otpService.verify(1L, 7L, OtpPurpose.CASH_WITHDRAWAL, tamperedOperation, "123456"))
                .hasMessageContaining("don't match what the customer approved");
        assertThat(otp.isConsumed()).isFalse();
    }

    @Test
    void verify_afterExpiry_isRejected() {
        Otp otp = otp("123456", NOW.minusSeconds(1));
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify(1L, 7L, OtpPurpose.CASH_WITHDRAWAL, OPERATION, "123456"))
                .extracting("errorCode").isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void verify_afterThreeWrongAttempts_rejectsEvenTheRightCode() {
        Otp otp = otp("123456", NOW.plusSeconds(60));
        otp.registerFailedAttempt();
        otp.registerFailedAttempt();
        otp.registerFailedAttempt();
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify(1L, 7L, OtpPurpose.CASH_WITHDRAWAL, OPERATION, "123456"))
                .extracting("errorCode").isEqualTo(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
    }

    @Test
    void verify_anotherCustomersOtp_isRejected() {
        Otp otp = otp("123456", NOW.plusSeconds(60));
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify(1L, 8L, OtpPurpose.CASH_WITHDRAWAL, OPERATION, "123456"))
                .extracting("errorCode").isEqualTo(ErrorCode.OTP_INVALID);
    }

    @Test
    void verify_aConsumedOtp_isRejected() {
        Otp otp = otp("123456", NOW.plusSeconds(60));
        otp.markConsumed();
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify(1L, 7L, OtpPurpose.CASH_WITHDRAWAL, OPERATION, "123456"))
                .hasMessageContaining("already been used");
    }

    // ---------- helpers ----------

    private Otp otp(String code, Instant expiresAt) {
        Otp otp = new Otp();
        otp.setUser(customer);
        otp.setPurpose(OtpPurpose.CASH_WITHDRAWAL);
        otp.setCodeHash(encoder.encode(code));
        otp.setOperationHash(HashUtils.sha256Hex(OPERATION));
        otp.setExpiresAt(expiresAt);
        otp.setMaxAttempts(3);
        return otp;
    }

    private static String extractCode(String emailBody) {
        Matcher matcher = Pattern.compile("Your OTP is (\\d{6})").matcher(emailBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}