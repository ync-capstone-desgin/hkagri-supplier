package com.hankooknongsan.supplier.notification;

import java.util.List;

import com.hankooknongsan.supplier.auth.LoginUser;
import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.ExpiryPolicy;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.common.RenewalClosedEvent;
import com.hankooknongsan.supplier.notification.NotificationDtos.DeliveryItem;
import com.hankooknongsan.supplier.notification.NotificationDtos.DeliveryLogItem;
import com.hankooknongsan.supplier.notification.NotificationDtos.NotificationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 서비스 (FR-011).
 * 내 알림 조회, 미확인 수, 처리상태 변경(미확인 → 확인 → 처리중 → 완료), 모두 확인,
 * 그리고 계약·인증이 갱신되면 관련 알림을 자동으로 완료 처리하는 기능을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final ExpiryPolicy policy;
    private final AppClock clock;

    /** 로그인 사용자의 알림을 만료일이 가까운 순으로 조회한다. actionStatus가 null이면 전체. */
    public PageResponse<NotificationItem> inbox(Long userId, ActionStatus actionStatus, Pageable pageable) {
        return PageResponse.of(notifications.findInbox(userId, actionStatus, pageable), this::toItem);
    }

    /** 로그인 사용자의 미확인 알림 수를 센다. (🔔 배지) */
    public long unreadCount(Long userId) {
        return notifications.countByRecipientIdAndActionStatus(userId, ActionStatus.UNREAD);
    }

    /** 알림의 처리상태를 바꾼다. 본인 알림만 바꿀 수 있으며, 관리자는 모든 알림을 바꿀 수 있다. */
    @Transactional
    public NotificationItem changeAction(Long id, LoginUser user, ActionStatus status) {
        Notification n = notifications.findById(id).orElseThrow(() -> BusinessException.notFound("알림"));
        if (!n.getRecipient().getId().equals(user.id()) && user.role() != Role.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인에게 온 알림만 처리할 수 있습니다.");
        }
        n.changeAction(status, clock.now());
        notifications.flush();
        return toItem(n);
    }

    /** 로그인 사용자의 미확인 알림을 모두 "확인"으로 바꾸고 바꾼 건수를 반환한다. */
    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = notifications.findByRecipientIdAndActionStatus(userId, ActionStatus.UNREAD);
        unread.forEach(n -> n.changeAction(ActionStatus.READ, clock.now()));
        return unread.size();
    }

    /** 최근 발송 기록을 조회한다. (관리자 발송 이력) */
    public List<DeliveryLogItem> recentDeliveries(int limit) {
        return deliveries.findRecent(Pageable.ofSize(limit)).stream().map(DeliveryLogItem::of).toList();
    }

    /**
     * 계약·인증이 갱신완료·갱신안함이 되면, 그 대상의 아직 완료되지 않은 알림을 모두 "완료"로 바꾼다.
     * 계약·인증 서비스가 보낸 {@link RenewalClosedEvent}를 같은 트랜잭션 안에서 받는다.
     */
    @EventListener
    @Transactional
    public void onRenewalClosed(RenewalClosedEvent event) {
        notifications.findByTargetTypeAndTargetIdAndActionStatusNot(event.targetType(), event.targetId(), ActionStatus.DONE)
                .forEach(n -> n.changeAction(ActionStatus.DONE, clock.now()));
    }

    /** 알림 엔티티를 오늘 기준 남은 일수가 포함된 응답 형식으로 변환한다. */
    private NotificationItem toItem(Notification n) {
        return new NotificationItem(n.getId(), n.getTargetType(), n.getTargetId(), n.getSupplier().getId(),
                n.getSupplier().getName(), n.getRuleDays(), n.getExpiryDate(), policy.daysLeft(n.getExpiryDate()),
                n.getTitle(), n.getMessage(), n.getActionStatus(), n.getActionAt(), n.getCreatedAt(),
                n.getDeliveries().stream().map(DeliveryItem::of).toList());
    }
}
