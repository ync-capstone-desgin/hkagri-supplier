package com.hankooknongsan.supplier.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 만료 판정 규칙 단위 테스트 (VT-004 오늘 만료, VT-005 기준일 경계).
 * 오늘을 2026-10-01로 고정하고, 만료예정 기간은 90일로 둔다.
 */
class ExpiryPolicyTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    final ExpiryPolicy policy = new ExpiryPolicy(AppClock.fixed(TODAY, ZoneId.of("Asia/Seoul")), 90);

    /** 오늘로부터 offset일 뒤 만료되는 항목의 남은 일수와 상태가 기대값과 같은지 확인한다. */
    @ParameterizedTest(name = "만료일 오늘{0}일 → {1}")
    @CsvSource({
            "-30, EXPIRED",
            "-1, EXPIRED",
            "0, IMMINENT",   // 오늘 만료는 만료예정 (VT-004)
            "7, IMMINENT",
            "8, IMMINENT",
            "30, IMMINENT",
            "31, IMMINENT",
            "90, IMMINENT",  // 기간 마지막 날까지 포함
            "91, NORMAL",
            "365, NORMAL",
    })
    void 남은_일수에_따라_만료상태를_판정한다(int offset, ExpiryStatus expected) {
        LocalDate expiry = TODAY.plusDays(offset);
        assertThat(policy.daysLeft(expiry)).isEqualTo(offset);
        assertThat(policy.status(expiry, false)).isEqualTo(expected);
    }

    /** 만료일이 없으면 계약은 무기한(정상), 인증은 확인필요로 본다. */
    @ParameterizedTest(name = "무기한 허용={0} → {1}")
    @CsvSource({"true, NORMAL", "false, UNKNOWN"})
    void 만료일이_없으면_대상에_따라_다르게_판정한다(boolean noExpiryIsNormal, ExpiryStatus expected) {
        assertThat(policy.daysLeft(null)).isNull();
        assertThat(policy.status(null, noExpiryIsNormal)).isEqualTo(expected);
    }
}
