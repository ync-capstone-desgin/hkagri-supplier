package com.hankooknongsan.supplier.expiry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.certification.Certification;
import com.hankooknongsan.supplier.certification.CertificationRepository;
import com.hankooknongsan.supplier.common.ExpiryPolicy;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.contract.Contract;
import com.hankooknongsan.supplier.contract.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료 예정 조회 서비스 (FR-007, FR-008).
 * 계약과 인증을 하나의 목록으로 합치고, 만료가 가까운 순(이미 만료된 것이 맨 앞)으로 정렬한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpiryService {

    /** 기간 하한이 없을 때 쓰는 아주 이른 날짜 */
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);

    /** 남은 일수가 적은 순, 만료일 없음은 맨 뒤 */
    private static final Comparator<ExpiryItem> URGENT_FIRST =
            Comparator.comparing(ExpiryItem::daysLeft, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ExpiryItem::supplierName);

    private final ContractRepository contracts;
    private final CertificationRepository certifications;
    private final ExpiryPolicy policy;

    /**
     * 조회 구간에 해당하는 계약·인증을 찾는다.
     *
     * @param period        조회 구간 (만료됨 / 7·30·90일 이내 / 확인필요)
     * @param targetType    대상 종류 (null이면 계약·인증 모두)
     * @param ownerId       내부 담당자 (null이면 전체)
     * @param renewalStatus 갱신 처리상태 (null이면 전체)
     */
    public List<ExpiryItem> find(ExpiryPeriod period, TargetType targetType, Long ownerId, RenewalStatus renewalStatus) {
        LocalDate today = policy.today();
        List<ExpiryItem> result = new ArrayList<>();
        boolean withContracts = targetType == null || targetType == TargetType.CONTRACT;
        boolean withCerts = targetType == null || targetType == TargetType.CERTIFICATION;

        if (period == ExpiryPeriod.UNKNOWN) {
            // 무기한 계약은 정상이므로 확인필요 대상은 만료일 없는 인증뿐이다.
            if (withCerts) {
                certifications.findWithoutExpiry(ownerId, renewalStatus).forEach(x -> result.add(toItem(x)));
            }
        } else {
            LocalDate from = period == ExpiryPeriod.EXPIRED ? MIN_DATE : today;
            LocalDate until = period == ExpiryPeriod.EXPIRED ? today.minusDays(1) : today.plusDays(period.days());
            if (withContracts) {
                contracts.findEnding(from, until, ownerId, renewalStatus).forEach(c -> result.add(toItem(c)));
            }
            if (withCerts) {
                certifications.findExpiring(from, until, ownerId, renewalStatus).forEach(x -> result.add(toItem(x)));
            }
        }
        result.sort(URGENT_FIRST);
        return result;
    }

    /**
     * 조치가 필요한 항목 전체(이미 만료 + 만료예정 기간 이내)를 급한 순으로 찾는다. (대시보드용)
     */
    public List<ExpiryItem> findActionable(Long ownerId) {
        LocalDate until = policy.horizon();
        List<ExpiryItem> result = new ArrayList<>();
        contracts.findEnding(MIN_DATE, until, ownerId, null).forEach(c -> result.add(toItem(c)));
        certifications.findExpiring(MIN_DATE, until, ownerId, null).forEach(x -> result.add(toItem(x)));
        result.sort(URGENT_FIRST);
        return result;
    }

    /** 계약을 만료 목록 행으로 변환한다. */
    private ExpiryItem toItem(Contract c) {
        AppUser owner = c.getOwner();
        return new ExpiryItem(TargetType.CONTRACT, c.getId(), c.getSupplier().getId(), c.getSupplier().getName(),
                c.getSupplier().getStatus(), c.getTitle(), c.getContractNumber(), c.getEndDate(),
                policy.daysLeft(c.getEndDate()), policy.status(c.getEndDate(), true), c.getRenewalStatus(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName());
    }

    /** 인증을 만료 목록 행으로 변환한다. */
    private ExpiryItem toItem(Certification x) {
        AppUser owner = x.getOwner();
        return new ExpiryItem(TargetType.CERTIFICATION, x.getId(), x.getSupplier().getId(),
                x.getSupplier().getName(), x.getSupplier().getStatus(), x.getCertType().getName(),
                x.getCertificateNumber(), x.getExpiryDate(), policy.daysLeft(x.getExpiryDate()),
                policy.status(x.getExpiryDate(), false), x.getRenewalStatus(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName());
    }
}
