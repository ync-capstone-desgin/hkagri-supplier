package com.hankooknongsan.supplier.notification.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DB 폴링 방식 발송기 (PoC 기본).
 * app.relay-delay-ms(기본 10초)마다 발송 대기열을 확인해 보낼 차례인 건을 처리한다.
 */
@Component
@RequiredArgsConstructor
public class DbPollingDeliveryRelay implements DeliveryRelay {

    private final DeliveryProcessor processor;

    /** 주기적으로 대기열을 확인한다. 이전 실행이 끝난 뒤 설정한 시간만큼 쉬고 다시 돈다. */
    @Scheduled(fixedDelayString = "${app.relay-delay-ms}")
    public void poll() {
        relay();
    }

    /** 대기열을 한 번 처리한다. (정기 실행 외에 시연용 [지금 점검]에서도 바로 호출한다) */
    @Override
    public int relay() {
        return processor.processDue();
    }
}
