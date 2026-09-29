package com.hankooknongsan.supplier.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.hankooknongsan.supplier.common.TargetType;
import jakarta.validation.constraints.NotNull;

/** 알림 API의 요청·응답 형식 모음 */
public final class NotificationDtos {

    /** DTO 모음 클래스이므로 생성을 막는다. */
    private NotificationDtos() {
    }

    /** 채널 하나의 발송 결과 요약 */
    public record DeliveryItem(Channel channel, DeliveryStatus status, int retryCount, String lastError,
                               LocalDateTime sentAt) {

        /** 발송 기록을 응답 형식으로 변환한다. */
        static DeliveryItem of(NotificationDelivery d) {
            return new DeliveryItem(d.getChannel(), d.getStatus(), d.getRetryCount(), d.getLastError(), d.getSentAt());
        }
    }

    /** 알림함의 알림 하나 (채널별 발송 결과 포함) */
    public record NotificationItem(Long id, TargetType targetType, Long targetId, Long supplierId,
                                   String supplierName, int ruleDays, LocalDate expiryDate, Integer daysLeft,
                                   String title, String message, ActionStatus actionStatus, LocalDateTime actionAt,
                                   LocalDateTime createdAt, List<DeliveryItem> deliveries) {
    }

    /** 처리상태 변경 요청 */
    public record ActionRequest(@NotNull(message = "처리상태를 선택해 주세요.") ActionStatus actionStatus) {
    }

    /** 관리자 발송 이력의 한 행 */
    public record DeliveryLogItem(Long id, Long notificationId, String recipientName, String recipientEmail,
                                  String title, Channel channel, DeliveryStatus status, int retryCount,
                                  String lastError, LocalDateTime createdAt, LocalDateTime sentAt,
                                  LocalDateTime nextAttemptAt) {

        /** 발송 기록을 발송 이력 행으로 변환한다. */
        static DeliveryLogItem of(NotificationDelivery d) {
            Notification n = d.getNotification();
            return new DeliveryLogItem(d.getId(), n.getId(), n.getRecipient().getName(), n.getRecipient().getEmail(),
                    n.getTitle(), d.getChannel(), d.getStatus(), d.getRetryCount(), d.getLastError(),
                    d.getCreatedAt(), d.getSentAt(), d.getNextAttemptAt());
        }
    }
}
