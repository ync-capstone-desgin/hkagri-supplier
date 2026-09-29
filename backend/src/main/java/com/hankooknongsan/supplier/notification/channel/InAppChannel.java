package com.hankooknongsan.supplier.notification.channel;

import java.util.Map;

import com.hankooknongsan.supplier.notification.Channel;
import com.hankooknongsan.supplier.notification.Notification;
import com.hankooknongsan.supplier.notification.NotificationDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 화면 알림 채널.
 * 알림은 이미 DB(알림함)에 저장돼 있으므로, 여기서는 접속 중인 화면에 실시간으로 알려주기만 한다.
 * 수신자가 접속 중이 아니어도 다음에 알림함을 열면 보이므로 발송 성공으로 본다.
 */
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    private final SseHub hub;

    /** 화면 채널을 담당한다. */
    @Override
    public Channel channel() {
        return Channel.IN_APP;
    }

    /** 수신자의 열린 화면에 "notification" 이벤트로 제목·본문을 밀어준다. */
    @Override
    public void send(NotificationDelivery delivery) {
        Notification n = delivery.getNotification();
        hub.send(n.getRecipient().getId(), "notification", Map.of(
                "id", n.getId(), "title", n.getTitle(), "message", n.getMessage(),
                "supplierId", n.getSupplier().getId()));
    }
}
