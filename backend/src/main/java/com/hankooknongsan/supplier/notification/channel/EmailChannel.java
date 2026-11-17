package com.hankooknongsan.supplier.notification.channel;

import com.hankooknongsan.supplier.notification.Channel;
import com.hankooknongsan.supplier.notification.Notification;
import com.hankooknongsan.supplier.notification.NotificationDelivery;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

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
    public void send(NotificationDelivery delivery) throws MessagingException {
        Notification n = delivery.getNotification();
        var message = mailSender.createMimeMessage();
        var mail = new MimeMessageHelper(message, true, "UTF-8");
        mail.setFrom(from);
        mail.setTo(n.getRecipient().getEmail());
        mail.setSubject("[한국농산] " + n.getTitle());
        String inbox = webUrl + "/notifications";
        String supplier = webUrl + "/suppliers/" + n.getSupplier().getId();
        String plain = """
                안녕하세요, %s님.
                한국농산 협력업체 통합관리 시스템에서 알려드립니다.

                %s

                ▶ 알림함에서 확인하기: %s
                ▶ 협력업체 상세: %s

                ──────────────────────────
                이 메일은 발신 전용이며 회신해도 확인하지 않습니다.
                알림 수신 설정은 마이페이지에서 바꿀 수 있습니다.
                한국농산 협력업체 통합관리 시스템 드림
                """.formatted(n.getRecipient().getName(), n.getMessage(), inbox, supplier);
        String html = """
                <div lang="ko" style="max-width:560px;background:#fff;color:#222;font-family:'Gmarket Sans','Malgun Gothic',sans-serif;line-height:1.6">
                  <p style="border-bottom:2px solid #222;padding-bottom:12px"><strong>한국농산</strong> · 협력업체 통합관리</p>
                  <h2>%s</h2>
                  <p>%s님, 안녕하세요.</p>
                  <p style="white-space:pre-line">%s</p>
                  <p><a style="color:#8a3048" href="%s">알림함에서 확인하기</a> · <a style="color:#8a3048" href="%s">협력업체 상세</a></p>
                  <p style="color:#595959;font-size:12px">이 메일은 발신 전용이며 회신해도 확인하지 않습니다.<br>
                    알림 수신 설정은 마이페이지에서 바꿀 수 있습니다.<br>한국농산 협력업체 통합관리 시스템 드림</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(n.getTitle()), HtmlUtils.htmlEscape(n.getRecipient().getName()),
                HtmlUtils.htmlEscape(n.getMessage()), HtmlUtils.htmlEscape(inbox), HtmlUtils.htmlEscape(supplier));
        mail.setText(plain, html);
        mailSender.send(message);
    }
}
