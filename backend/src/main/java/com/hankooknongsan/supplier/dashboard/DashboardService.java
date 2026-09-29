package com.hankooknongsan.supplier.dashboard;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.hankooknongsan.supplier.certification.CertificationRepository;
import com.hankooknongsan.supplier.common.ExpiryPolicy;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.expiry.ExpiryItem;
import com.hankooknongsan.supplier.expiry.ExpiryPeriod;
import com.hankooknongsan.supplier.expiry.ExpiryService;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 집계 서비스 (FR-009).
 * 만료 예정 목록과 같은 조회({@link ExpiryService})를 바탕으로 세므로, 카드의 숫자와 목록 건수가 항상 일치한다 (VT-009).
 * PoC 규모(수백 건)를 가정해 조치 대상을 한 번 조회한 뒤 메모리에서 구간별로 센다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** 대시보드에 보여줄 급한 항목 수 */
    private static final int URGENT_LIMIT = 10;

    private final SupplierRepository suppliers;
    private final ContractRepository contracts;
    private final CertificationRepository certifications;
    private final ExpiryService expiryService;
    private final ExpiryPolicy policy;

    /** 로그인 사용자 기준으로 대시보드 요약을 만든다. */
    public DashboardSummary summary(Long userId) {
        List<ExpiryItem> actionable = expiryService.findActionable(null);
        long unknown = expiryService.find(ExpiryPeriod.UNKNOWN, null, null, null).size();

        return new DashboardSummary(
                policy.today(),
                suppliers.count(),
                suppliers.countByStatus(SupplierStatus.ACTIVE),
                contracts.countByActiveTrue(),
                certifications.countByActiveTrue(),
                count(actionable, e -> e.daysLeft() < 0),
                count(actionable, e -> e.daysLeft() >= 0 && e.daysLeft() <= 7),
                count(actionable, e -> e.daysLeft() >= 0 && e.daysLeft() <= 30),
                count(actionable, e -> e.daysLeft() >= 0 && e.daysLeft() <= 90),
                unknown,
                count(actionable, e -> e.renewalStatus() == RenewalStatus.IN_PROGRESS),
                count(actionable, e -> e.renewalStatus() == RenewalStatus.NONE),
                count(actionable, e -> Objects.equals(e.ownerId(), userId)),
                actionable.stream().limit(URGENT_LIMIT).toList());
    }

    /** 조건에 맞는 항목 수를 센다. */
    private static long count(List<ExpiryItem> items, Predicate<ExpiryItem> condition) {
        return items.stream().filter(condition).count();
    }
}
