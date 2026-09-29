package com.hankooknongsan.supplier.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 정기 작업(만료 점검, 알림 발송) 활성화 설정.
 * app.scheduling-enabled=false면 끈다. 테스트에서는 작업을 직접 호출해 결과를 확인하기 위해 끈다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
