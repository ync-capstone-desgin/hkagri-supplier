package com.hankooknongsan.supplier.notification.rule;

import java.util.List;

import com.hankooknongsan.supplier.notification.rule.NotificationRuleService.RuleItem;
import com.hankooknongsan.supplier.notification.rule.NotificationRuleService.RuleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 알림 기준 설정 API. 조회는 로그인 사용자 모두, 변경은 관리자만 (SecurityConfig: PUT /api/settings/**). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/settings/notification-rules")
public class NotificationRuleController {

    private final NotificationRuleService service;

    /** 알림 기준 목록을 조회한다. */
    @GetMapping
    public List<RuleItem> list() {
        return service.list();
    }

    /** 알림 기준을 바꾼다. (관리자) */
    @PutMapping("/{id}")
    public RuleItem update(@PathVariable Long id, @Valid @RequestBody RuleRequest req) {
        return service.update(id, req);
    }
}
