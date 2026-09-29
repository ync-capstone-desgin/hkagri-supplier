package com.hankooknongsan.supplier.notification;

import java.util.List;
import java.util.Map;

import com.hankooknongsan.supplier.auth.LoginUser;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.notification.NotificationDtos.ActionRequest;
import com.hankooknongsan.supplier.notification.NotificationDtos.DeliveryLogItem;
import com.hankooknongsan.supplier.notification.NotificationDtos.NotificationItem;
import com.hankooknongsan.supplier.notification.channel.SseHub;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림 API (FR-010, FR-011).
 * 알림함, 미확인 수, 처리상태 변경, 모두 확인, 실시간 연결(SSE), 관리자 발송 이력을 제공한다.
 */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final SseHub sseHub;

    /** 내 알림함을 만료일이 가까운 순으로 조회한다. */
    @GetMapping("/api/notifications")
    public PageResponse<NotificationItem> inbox(@AuthenticationPrincipal LoginUser user,
                                                @RequestParam(required = false) ActionStatus actionStatus,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return service.inbox(user.id(), actionStatus, PageRequest.of(page, size));
    }

    /** 내 미확인 알림 수를 반환한다. */
    @GetMapping("/api/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal LoginUser user) {
        return Map.of("count", service.unreadCount(user.id()));
    }

    /** 알림의 처리상태를 바꾼다. */
    @PatchMapping("/api/notifications/{id}/action")
    public NotificationItem changeAction(@PathVariable Long id, @AuthenticationPrincipal LoginUser user,
                                         @Valid @RequestBody ActionRequest req) {
        return service.changeAction(id, user, req.actionStatus());
    }

    /** 내 미확인 알림을 모두 확인 처리한다. */
    @PostMapping("/api/notifications/read-all")
    public Map<String, Integer> readAll(@AuthenticationPrincipal LoginUser user) {
        return Map.of("updated", service.markAllRead(user.id()));
    }

    /** 실시간 알림 연결을 연다. 새 알림이 생기면 "notification" 이벤트가 온다. */
    @GetMapping(path = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal LoginUser user) {
        return sseHub.subscribe(user.id());
    }

    /** 최근 발송 기록 200건을 조회한다. (관리자) */
    @GetMapping("/api/admin/deliveries")
    public List<DeliveryLogItem> deliveries() {
        return service.recentDeliveries(200);
    }
}
