package com.coretrust.bank.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilsTest {

    @Test
    void accountNumber_showsOnlyTheLastFourDigits() {
        assertThat(MaskingUtils.maskAccountNumber("100107389094")).isEqualTo("XXXXXXXX9094");
    }

    @Test
    void email_keepsFirstAndLastLetterAndDomain() {
        assertThat(MaskingUtils.maskEmail("sathya@test.com")).isEqualTo("s****a@test.com");
        assertThat(MaskingUtils.maskEmail("ab@test.com")).isEqualTo("a***@test.com");
    }

    @Test
    void name_keepsOnlyTheFirstLetterOfEachWord() {
        assertThat(MaskingUtils.maskName("Ram Kumar")).isEqualTo("R** K****");
    }

    @Test
    void phone_showsOnlyTheLastFourDigits() {
        assertThat(MaskingUtils.maskPhone("9876543210")).isEqualTo("******3210");
    }
}