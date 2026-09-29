package com.hankooknongsan.supplier.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 전체에서 "오늘"과 "현재 시각"을 판단하는 기준 시계.
 * <p>
 * {@code LocalDate.now()}를 직접 부르지 않고 이 클래스를 통해 날짜를 얻는다.
 * 시연·테스트에서 기준일을 원하는 날짜로 옮길 수 있도록 일 단위 오프셋을 가진다.
 * 시간대는 설정값(app.zone, 기본 Asia/Seoul)으로 고정한다.
 */
@Component
public class AppClock {

    private final Clock base;
    private final AtomicLong offsetDays = new AtomicLong();

    /** 설정된 시간대의 시스템 시계로 생성한다. */
    @Autowired
    public AppClock(@Value("${app.zone}") String zone) {
        this(Clock.system(ZoneId.of(zone)));
    }

    /** 테스트용: 임의의 시계로 생성한다. */
    AppClock(Clock base) {
        this.base = base;
    }

    /** 테스트용: 항상 지정한 날짜를 오늘로 돌려주는 시계를 만든다. */
    public static AppClock fixed(LocalDate today, ZoneId zone) {
        return new AppClock(Clock.fixed(today.atStartOfDay(zone).toInstant(), zone));
    }

    /** 기준 시간대의 오늘 날짜(오프셋 반영)를 반환한다. */
    public LocalDate today() {
        return LocalDate.now(base).plusDays(offsetDays.get());
    }

    /** 기준 시간대의 현재 일시(오프셋 반영)를 반환한다. */
    public LocalDateTime now() {
        return LocalDateTime.now(base).plusDays(offsetDays.get());
    }

    /** 기준 시간대를 반환한다. */
    public ZoneId zone() {
        return base.getZone();
    }

    /** 오늘을 지정한 날짜로 옮긴다. null이면 실제 날짜로 되돌린다. (시연용) */
    public void setToday(LocalDate date) {
        offsetDays.set(date == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(base), date));
    }

    /** 기준일이 실제 날짜에서 옮겨져 있는지 여부를 반환한다. */
    public boolean isShifted() {
        return offsetDays.get() != 0;
    }
}
