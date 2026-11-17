package com.hankooknongsan.supplier.notification.channel;

import com.hankooknongsan.supplier.notification.Notification;
import com.hankooknongsan.supplier.notification.NotificationDelivery;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** SMTP 연결 없이 실제 발송 메시지의 두 본문과 링크를 확인한다. */
class EmailChannelTest {

    @Test
    void 일반텍스트와_HTML에_같은_링크를_넣고_입력값은_문자로_표시한다() throws Exception {
        var sender = mock(JavaMailSender.class);
        var mail = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mail);
        var channel = new EmailChannel(sender);
        ReflectionTestUtils.setField(channel, "from", "demo@example.com");
        ReflectionTestUtils.setField(channel, "webUrl", "https://example.com");
        var delivery = mock(NotificationDelivery.class);
        var notification = mock(Notification.class, RETURNS_DEEP_STUBS);
        when(delivery.getNotification()).thenReturn(notification);
        when(notification.getRecipient().getEmail()).thenReturn("user@example.com");
        when(notification.getRecipient().getName()).thenReturn("김담당");
        when(notification.getSupplier().getId()).thenReturn(42L);
        when(notification.getTitle()).thenReturn("계약 만료");
        when(notification.getMessage()).thenReturn("과일 <공급> & 계약");

        channel.send(delivery);
        verify(sender).send(mail);
        mail.saveChanges();
        assertThat(mail.getSubject()).isEqualTo("[한국농산 협력업체 통합관리] 계약 만료");
        Multipart parts = (Multipart) mail.getContent();
        while (!parts.getContentType().startsWith("multipart/alternative")) {
            parts = (Multipart) parts.getBodyPart(0).getContent();
        }
        var plain = parts.getBodyPart(0);
        var html = parts.getBodyPart(1);
        assertThat(plain.isMimeType("text/plain")).isTrue();
        assertThat(html.isMimeType("text/html")).isTrue();
        assertThat(plain.getContent().toString()).contains("과일 <공급> & 계약", "https://example.com/notifications", "https://example.com/suppliers/42");
        assertThat(html.getContent().toString()).contains("과일 &lt;공급&gt; &amp; 계약", "href=\"https://example.com/notifications\"", "href=\"https://example.com/suppliers/42\"");
    }
}
