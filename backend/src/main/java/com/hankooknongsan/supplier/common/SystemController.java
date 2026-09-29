package com.hankooknongsan.supplier.common;

import java.time.LocalDate;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시스템 정보 API.
 * 화면 상단에 현재 기준일을 보여주고, 시연용으로 기준일을 옮긴 상태라면 눈에 띄게 표시하는 데 쓴다.
 */
@RestController
@RequiredArgsConstructor
public class SystemController {

    private final AppClock clock;
    private final ExpiryPolicy policy;
    private final Environment environment;

    /**
     * 기준일 정보
     *
     * @param today           판정 기준 오늘 날짜
     * @param shifted         시연용으로 실제 날짜에서 옮겨져 있는지
     * @param demoClock       기준일 변경 기능을 쓸 수 있는지(dev 프로파일)
     * @param horizonDays     만료예정으로 보는 기간(일)
     */
    public record ClockInfo(LocalDate today, boolean shifted, boolean demoClock, int horizonDays) {
    }

    /** 현재 기준일과 시연용 기준일 변경 가능 여부를 반환한다. */
    @GetMapping("/api/system/clock")
    public ClockInfo clock() {
        boolean dev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        return new ClockInfo(clock.today(), clock.isShifted(), dev, policy.horizonDays());
    }
}
