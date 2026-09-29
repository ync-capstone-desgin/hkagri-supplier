package com.hankooknongsan.supplier.dashboard;

import com.hankooknongsan.supplier.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드 API (FR-009) */
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    /** 로그인 사용자의 대시보드 요약(카드 숫자, 급한 항목, 내 담당 건수)을 반환한다. */
    @GetMapping("/api/dashboard/summary")
    public DashboardSummary summary(@AuthenticationPrincipal LoginUser user) {
        return service.summary(user == null ? null : user.id());
    }
}
