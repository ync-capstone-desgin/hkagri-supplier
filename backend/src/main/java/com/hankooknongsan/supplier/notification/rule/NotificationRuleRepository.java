package com.hankooknongsan.supplier.notification.rule;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 알림 기준 저장소 */
public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    /** 사용 중인 알림 기준만 조회한다. */
    List<NotificationRule> findByEnabledTrue();
}
