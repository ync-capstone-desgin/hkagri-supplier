package com.hankooknongsan.supplier.notification.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 정해진 시각(app.scan-cron, 기본 08:00 KST)에 만료 점검을 실행하는 정기 작업.
 * 서버를 여러 대로 늘리면 ShedLock 등으로 한 대에서만 돌게 해야 한다. (PoC는 한 대 가정)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiryScanJob {

    private final NotificationScanService scanService;

    /** 오늘 기준으로 만료 점검을 실행하고 결과를 로그로 남긴다. */
    @Scheduled(cron = "${app.scan-cron}", zone = "${app.zone}")
    public void run() {
        var result = scanService.scanToday();
        log.info("만료 점검 완료: 기준일={}, 대상={}, 생성={}, 중복건너뜀={}, 제외={}",
                result.today(), result.targets(), result.created(), result.skipped(), result.excluded());
    }
}
