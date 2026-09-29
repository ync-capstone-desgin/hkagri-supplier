package com.hankooknongsan.supplier.dev;

import com.hankooknongsan.supplier.notification.delivery.DeliveryRelay;
import com.hankooknongsan.supplier.notification.job.NotificationScanService;
import com.hankooknongsan.supplier.notification.job.NotificationScanService.ScanResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시연용 작업 실행 API (dev 프로파일 + 관리자 전용).
 * 매일 08시까지 기다리지 않고 만료 점검과 발송을 바로 실행해 알림이 오는 모습을 보여준다.
 */
@Profile("dev")
@RestController
@RequiredArgsConstructor
public class DevJobController {

    private final NotificationScanService scanService;
    private final DeliveryRelay relay;

    /**
     * 점검 + 발송 결과
     *
     * @param scan      만료 점검 결과
     * @param delivered 바로 처리한 발송 건수
     */
    public record RunResult(ScanResult scan, int delivered) {
    }

    /** 현재 기준일로 만료 점검을 실행하고, 생성된 알림을 바로 발송한다. */
    @PostMapping("/api/admin/jobs/expiry-scan")
    public RunResult runScan() {
        ScanResult scan = scanService.scanToday();
        return new RunResult(scan, relay.relay());
    }
}
