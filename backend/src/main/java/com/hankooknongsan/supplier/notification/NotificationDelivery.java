package com.hankooknongsan.supplier.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채널별 발송 기록이자 발송 대기열(아웃박스).
 * <p>
 * 알림 생성과 같은 트랜잭션에서 PENDING으로 저장되고, 발송기가 주기적으로 꺼내 보낸다.
 * 발송에 실패해도 업무 데이터(알림)는 그대로 남고, 이 행에만 실패 사유와 재시도 횟수가 기록된다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery {

    /** 최대 재시도 횟수. 넘으면 실패로 확정한다. */
    public static final int MAX_RETRY = 5;

    /** 첫 재시도까지 기다리는 시간(초). 이후 두 배씩 늘린다: 30초, 1분, 2분, 4분 … */
    static final long BASE_BACKOFF_SECONDS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 발송할 알림 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    /** 발송 채널 */
    @Enumerated(EnumType.STRING)
    private Channel channel;

    /** 발송 상태 */
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    /** 지금까지 실패한 횟수 */
    private int retryCount;

    /** 다음에 발송을 시도할 일시 */
    private LocalDateTime nextAttemptAt;

    /** 마지막 실패 사유 */
    private String lastError;

    /** 발송 성공 일시 */
    private LocalDateTime sentAt;

    /** 생성 일시 */
    private LocalDateTime createdAt;

    /** 바로 발송할 수 있는 대기 건을 만든다. */
    NotificationDelivery(Notification notification, Channel channel, LocalDateTime now) {
        this.notification = notification;
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    /** 발송 성공으로 기록한다. */
    public void markSent(LocalDateTime now) {
        this.status = DeliveryStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    /**
     * 발송 실패를 기록한다.
     * 재시도 횟수가 남았으면 기다리는 시간을 두 배씩 늘려 다시 대기시키고, 다 쓰면 FAILED로 확정한다.
     */
    public void markFailed(String error, LocalDateTime now) {
        this.retryCount++;
        this.lastError = error == null ? "알 수 없는 오류" : error.substring(0, Math.min(error.length(), 500));
        if (retryCount >= MAX_RETRY) {
            this.status = DeliveryStatus.FAILED;
        } else {
            this.nextAttemptAt = now.plusSeconds(BASE_BACKOFF_SECONDS << (retryCount - 1));
        }
    }
}
