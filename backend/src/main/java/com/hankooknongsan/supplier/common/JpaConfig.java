package com.hankooknongsan.supplier.common;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA 감사(Auditing) 설정.
 * 등록·수정 일시는 {@link AppClock} 기준으로, 등록·수정자는 로그인 사용자 아이디로 채운다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")
public class JpaConfig {

    /** 등록·수정 일시를 AppClock의 현재 시각으로 제공한다. */
    @Bean
    DateTimeProvider auditDateTimeProvider(AppClock clock) {
        return () -> Optional.of(clock.now());
    }

    /** 현재 로그인 사용자 아이디를 제공한다. 로그인 전(배치·초기 데이터)에는 "system"으로 기록한다. */
    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth instanceof AnonymousAuthenticationToken) {
                return Optional.of("system");
            }
            return Optional.of(auth.getName());
        };
    }
}
