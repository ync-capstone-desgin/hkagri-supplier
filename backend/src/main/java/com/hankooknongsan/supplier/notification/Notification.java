package com.hankooknongsan.supplier.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.supplier.Supplier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 만료 알림 (FR-010, FR-011).
 * <p>
 * "어떤 대상(계약·인증)이, 어떤 기준(만료 N일 전)에, 어떤 만료일로, 누구에게" 알렸는지를 기록한다.
 * 이 네 값 + 대상 ID 조합은 DB 고유 제약(uk_notification_once)으로 한 번만 저장되므로
 * 점검 작업을 여러 번 돌려도 같은 알림이 중복 생성되지 않는다 (VT-006).
 * 만료일이 조합에 들어 있어, 갱신으로 만료일이 바뀌면 새 만료일 기준 알림은 다시 생성된다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 종류 (계약/인증) */
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    /** 계약 또는 인증 ID */
    private Long targetId;

    /** 적용된 알림 기준 (만료 N일 전) */
    private int ruleDays;

    /** 알림 생성 시점의 만료일 */
    private LocalDate expiryDate;

    /** 수신자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private AppUser recipient;

    /** 대상의 협력업체 (화면 이동·표시용) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** 알림 제목. 예: [D-7] 가람농산 · GAP 인증 만료 예정 */
    private String title;

    /** 알림 본문 */
    private String message;

    /** 수신자의 업무 처리상태 */
    @Enumerated(EnumType.STRING)
    private ActionStatus actionStatus;

    /** 처리상태를 마지막으로 바꾼 일시 */
    private LocalDateTime actionAt;

    /** 생성 일시 */
    private LocalDateTime createdAt;

    /** 채널별 발송 기록 */
    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL)
    @OrderBy("channel")
    private List<NotificationDelivery> deliveries = new ArrayList<>();

    /** 새 알림을 "미확인" 상태로 만든다. */
    public Notification(TargetType targetType, Long targetId, int ruleDays, LocalDate expiryDate, AppUser recipient,
                        Supplier supplier, String title, String message, LocalDateTime createdAt) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.ruleDays = ruleDays;
        this.expiryDate = expiryDate;
        this.recipient = recipient;
        this.supplier = supplier;
        this.title = title;
        this.message = message;
        this.actionStatus = ActionStatus.UNREAD;
        this.createdAt = createdAt;
    }

    /** 채널별 발송 대기 건을 추가한다. 알림과 같은 트랜잭션으로 저장된다 (아웃박스 패턴). */
    public void addDelivery(Channel channel, LocalDateTime now) {
        deliveries.add(new NotificationDelivery(this, channel, now));
    }

    /** 업무 처리상태를 바꾼다. */
    public void changeAction(ActionStatus status, LocalDateTime now) {
        this.actionStatus = status;
        this.actionAt = now;
    }
}
