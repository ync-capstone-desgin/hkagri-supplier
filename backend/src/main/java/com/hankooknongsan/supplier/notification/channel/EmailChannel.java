package com.hankooknongsan.supplier.notification.channel;

import com.hankooknongsan.supplier.notification.Channel;
import com.hankooknongsan.supplier.notification.Notification;
import com.hankooknongsan.supplier.notification.NotificationDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 이메일 채널.
 * 개발 환경에서는 Mailpit(로컬 SMTP)으로 보내 실제 메일은 나가지 않고 http://localhost:8025 에서 확인한다.
 * 실제 서비스에서는 사내 SMTP 또는 메일 발송 서비스 주소로 설정만 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    /** 발신 주소 */
    @Value("${app.mail-from}")
    private String from;

    /** 메일 본문에 넣을 화면 주소 */
    @Value("${app.web-url}")
    private String webUrl;

    /** 이메일 채널을 담당한다. */
    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    /** 수신자에게 알림 제목·본문과 알림함 링크를 담은 메일을 보낸다. 실패하면 메일 서버 예외가 그대로 올라간다. */
    @Override
    public void send(NotificationDelivery delivery) {
        Notification n = delivery.getNotification();
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(n.getRecipient().getEmail());
        mail.setSubject("[협력업체 통합관리] " + n.getTitle());
        mail.setText("""
                %s님, 안녕하세요.

                %s

                ▶ 알림함에서 확인하기: %s/notifications
                ▶ 협력업체 상세: %s/suppliers/%d

                ※ 이 메일은 협력업체 통합관리 PoC에서 자동 발송되었습니다.
                """.formatted(n.getRecipient().getName(), n.getMessage(), webUrl, webUrl, n.getSupplier().getId()));
        mailSender.send(mail);
    }
}
