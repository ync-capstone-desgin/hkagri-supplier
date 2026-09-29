package com.hankooknongsan.supplier.dev;

import java.time.LocalDate;

import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.SystemController;
import com.hankooknongsan.supplier.common.SystemController.ClockInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시연용 기준일 변경 API (dev 프로파일 + 관리자 전용).
 * 발표 때 "3개월 뒤" 같은 상황을 재현하기 위해 시스템의 오늘 날짜를 옮긴다.
 * 서버를 재시작하면 실제 날짜로 돌아간다.
 */
@Profile("dev")
@RestController
@RequiredArgsConstructor
public class DevClockController {

    private final AppClock clock;
    private final SystemController systemController;

    /** 기준일 변경 요청. date가 null이면 실제 날짜로 되돌린다. */
    public record ClockRequest(LocalDate date) {
    }

    /** 기준일을 지정한 날짜로 옮기거나(null이면) 실제 날짜로 되돌린다. */
    @PutMapping("/api/admin/clock")
    public ClockInfo setClock(@RequestBody ClockRequest req) {
        clock.setToday(req.date());
        return systemController.clock();
    }
}
