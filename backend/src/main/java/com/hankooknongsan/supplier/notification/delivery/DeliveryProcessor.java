package com.hankooknongsan.supplier.notification.delivery;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.notification.Channel;
import com.hankooknongsan.supplier.notification.NotificationDelivery;
import com.hankooknongsan.supplier.notification.NotificationDeliveryRepository;
import com.hankooknongsan.supplier.notification.channel.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 대기열(아웃박스)에서 보낼 차례인 건을 꺼내 채널별로 보내고 결과를 기록한다.
 * 한 번에 최대 50건씩 처리하며, 한 건이 실패해도 나머지는 계속 보낸다.
 */
@Slf4j
@Service
public class DeliveryProcessor {

    /** 한 번에 처리할 최대 건수 */
    static final int BATCH_SIZE = 50;

    private final NotificationDeliveryRepository deliveries;
    private final Map<Channel, NotificationChannel> channels;
    private final AppClock clock;

    /** 등록된 채널 구현체를 채널 종류별로 묶어 둔다. */
    public DeliveryProcessor(NotificationDeliveryRepository deliveries, List<NotificationChannel> channels, AppClock clock) {
        this.deliveries = deliveries;
        this.channels = channels.stream().collect(Collectors.toMap(NotificationChannel::channel, Function.identity()));
        this.clock = clock;
    }

    /**
     * 보낼 차례인 대기 건을 처리하고 처리한 건수를 반환한다.
     * 성공하면 발송완료, 실패하면 재시도 대기(최대 5회) 또는 실패로 기록한다.
     */
    @Transactional
    public int processDue() {
        List<NotificationDelivery> due = deliveries.findDue(clock.now(), Pageable.ofSize(BATCH_SIZE));
        for (NotificationDelivery d : due) {
            try {
                NotificationChannel channel = channels.get(d.getChannel());
                if (channel == null) {
                    throw new IllegalStateException("지원하지 않는 채널: " + d.getChannel());
                }
                channel.send(d);
                d.markSent(clock.now());
            } catch (Exception e) {
                log.warn("알림 발송 실패: deliveryId={}, channel={}, 사유={}", d.getId(), d.getChannel(), e.getMessage());
                d.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage(), clock.now());
            }
        }
        return due.size();
    }
}
