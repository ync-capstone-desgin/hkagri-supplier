package com.hankooknongsan.supplier.notification.channel;

import com.hankooknongsan.supplier.notification.Channel;
import com.hankooknongsan.supplier.notification.NotificationDelivery;

/**
 * 알림 발송 채널 구현체가 따르는 규칙.
 * 문자·메신저 채널을 추가하려면 이 인터페이스를 구현한 클래스를 하나 더 만들면 된다.
 */
public interface NotificationChannel {

    /** 이 구현체가 담당하는 채널 */
    Channel channel();

    /** 발송 건 하나를 보낸다. 실패하면 예외를 던지고, 발송기가 재시도 여부를 판단한다. */
    void send(NotificationDelivery delivery) throws Exception;
}
