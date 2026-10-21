package com.hankooknongsan.supplier.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 사업자번호 검증번호 계산 단위 테스트 (DB 없이 실행) */
class BusinessNumberTest {

    /** 공개된 실제 사업자번호는 검증번호가 맞고, 마지막 자리를 바꾸면 틀린 번호가 된다. */
    @Test
    void 실제_사업자번호는_통과하고_마지막_자리가_틀리면_실패한다() {
        assertThat(BusinessNumber.isValid("124-81-00998")).isTrue();
        assertThat(BusinessNumber.isValid("1248100998")).isTrue();
        assertThat(BusinessNumber.isValid("124-81-00999")).isFalse();
    }

    /** 숫자 10자리가 아니면 실패한다. */
    @Test
    void 자릿수가_틀리거나_숫자가_아니면_실패한다() {
        assertThat(BusinessNumber.isValid(null)).isFalse();
        assertThat(BusinessNumber.isValid("124-81-0099")).isFalse();
        assertThat(BusinessNumber.isValid("124-81-0099a")).isFalse();
    }

    /** 앞 9자리로 만든 번호는 항상 검증을 통과하고 000-00-00000 형식이다. */
    @Test
    void 검증번호를_붙여_만든_번호는_항상_통과한다() {
        for (int i = 0; i < 1000; i++) {
            String number = BusinessNumber.withCheckDigit("%09d".formatted(i * 999_331L % 1_000_000_000L));
            assertThat(number).matches("\\d{3}-\\d{2}-\\d{5}");
            assertThat(BusinessNumber.isValid(number)).isTrue();
        }
    }
}
