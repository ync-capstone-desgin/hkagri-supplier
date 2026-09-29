package com.hankooknongsan.supplier.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 만료 판정 규칙 (FR-007). 만료상태 계산은 반드시 이 클래스 한 곳에서만 한다.
 * <pre>
 * 남은 일수 = 만료일 - 오늘(Asia/Seoul)
 *
 * 만료일 없음       → 계약: 정상(무기한) / 인증: 확인필요
 * 남은 일수 < 0     → 만료
 * 0 ≤ 남은 일수 ≤ N → 만료예정 (N = app.expiry-horizon-days, 기본 90)
 * 남은 일수 > N     → 정상
 * </pre>
 * 오늘 만료되는 항목(D-0)은 "만료예정"으로 본다. (가설, 미팅 확인 필요)
 */
@Component
public class ExpiryPolicy {

    private final AppClock clock;
    private final int horizonDays;

    /** 기준 시계와 만료예정 기간(일)으로 만든다. */
    public ExpiryPolicy(AppClock clock, @Value("${app.expiry-horizon-days}") int horizonDays) {
        this.clock = clock;
        this.horizonDays = horizonDays;
    }

    /** 판정 기준이 되는 오늘 날짜를 반환한다. */
    public LocalDate today() {
        return clock.today();
    }

    /** 만료예정으로 보는 마지막 날짜(오늘 + N일)를 반환한다. */
    public LocalDate horizon() {
        return today().plusDays(horizonDays);
    }

    /** 만료예정 기간(일)을 반환한다. */
    public int horizonDays() {
        return horizonDays;
    }

    /** 만료일까지 남은 일수를 계산한다. 오늘 만료면 0, 이미 지났으면 음수, 만료일이 없으면 null. */
    public Integer daysLeft(LocalDate expiryDate) {
        return expiryDate == null ? null : (int) ChronoUnit.DAYS.between(today(), expiryDate);
    }

    /**
     * 만료상태를 판정한다.
     *
     * @param expiryDate    만료일(계약은 종료일)
     * @param noExpiryIsNormal 만료일이 없을 때 정상으로 볼지 여부 (무기한 계약은 true, 인증은 false)
     */
    public ExpiryStatus status(LocalDate expiryDate, boolean noExpiryIsNormal) {
        if (expiryDate == null) {
            return noExpiryIsNormal ? ExpiryStatus.NORMAL : ExpiryStatus.UNKNOWN;
        }
        int days = daysLeft(expiryDate);
        if (days < 0) {
            return ExpiryStatus.EXPIRED;
        }
        return days <= horizonDays ? ExpiryStatus.IMMINENT : ExpiryStatus.NORMAL;
    }
}
