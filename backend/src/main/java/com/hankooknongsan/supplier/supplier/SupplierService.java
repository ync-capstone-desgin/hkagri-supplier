package com.hankooknongsan.supplier.supplier;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierDetail;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierListItem;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierRequest;
import com.hankooknongsan.supplier.supplier.SupplierRepository.SupplierCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협력업체 등록·조회·수정·검색 업무 서비스 (FR-001~004).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    /** 목록의 "만료건수" 기준: 만료됐거나 오늘부터 90일 안에 만료되는 유효 계약·인증 */
    static final int EXPIRING_HORIZON_DAYS = 90;

    private final SupplierRepository suppliers;
    private final AppUserRepository users;
    private final AppClock clock;

    /** 조건에 맞는 협력업체 목록을 페이지 단위로 조회한다. */
    public PageResponse<SupplierListItem> search(String keyword, SupplierStatus status, String certType,
                                                 Pageable pageable) {
        Page<Supplier> page = suppliers.search(nullToEmpty(keyword).trim(), status, nullToEmpty(certType), pageable);
        Map<Long, SupplierCounts> counts = countsOf(page.getContent());
        return PageResponse.of(page, s -> toListItem(s, counts.get(s.getId())));
    }

    /** 조건에 맞는 협력업체 전체를 조회한다. (엑셀 다운로드용) */
    public List<SupplierListItem> searchAll(String keyword, SupplierStatus status, String certType) {
        return search(keyword, status, certType, Pageable.ofSize(10_000)).content();
    }

    /** 협력업체 한 곳의 상세정보를 조회한다. 없으면 404. */
    public SupplierDetail get(Long id) {
        Supplier s = find(id);
        SupplierCounts c = countsOf(List.of(s)).get(id);
        AppUser owner = s.getOwner();
        return new SupplierDetail(s.getId(), s.getCode(), s.getName(), s.getBusinessNumber(), s.getStatus(),
                s.getContactName(), s.getContactPhone(), s.getContactEmail(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(), s.getMemo(),
                c.getContractCount(), c.getCertCount(), c.getExpiringCount(),
                s.getCreatedAt(), s.getCreatedBy(), s.getUpdatedAt(), s.getUpdatedBy());
    }

    /**
     * 협력업체를 등록한다.
     * 저장 후 발급된 ID로 업체코드(SUP-0001 형식)를 부여한다.
     */
    @Transactional
    public SupplierDetail create(SupplierRequest req) {
        Supplier s = new Supplier(req.name(), req.status());
        apply(s, req);
        suppliers.save(s);
        s.assignCode("SUP-%04d".formatted(s.getId()));
        suppliers.flush();
        return get(s.getId());
    }

    /** 협력업체 기본정보를 수정한다. */
    @Transactional
    public SupplierDetail update(Long id, SupplierRequest req) {
        apply(find(id), req);
        suppliers.flush();
        return get(id);
    }

    /** ID로 협력업체 엔티티를 찾는다. 다른 기능(계약·인증)에서도 업체 확인에 사용한다. */
    public Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> BusinessException.notFound("협력업체"));
    }

    /** 요청 값을 엔티티에 반영한다. 담당자 ID가 잘못되면 입력 오류, 빈 문자열은 null로 저장한다. */
    private void apply(Supplier s, SupplierRequest req) {
        AppUser owner = req.ownerId() == null ? null
                : users.findById(req.ownerId()).orElseThrow(() -> BusinessException.invalid("ownerId", "담당자를 찾을 수 없습니다."));
        s.update(req.name().trim(), blankToNull(req.businessNumber()), req.status(), blankToNull(req.contactName()),
                blankToNull(req.contactPhone()), blankToNull(req.contactEmail()), owner, blankToNull(req.memo()));
    }

    /** 업체들의 계약·인증 건수를 한 번에 조회해 업체 ID별로 묶는다. */
    private Map<Long, SupplierCounts> countsOf(List<Supplier> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return suppliers.counts(list.stream().map(Supplier::getId).toList(),
                        clock.today().plusDays(EXPIRING_HORIZON_DAYS))
                .stream().collect(Collectors.toMap(SupplierCounts::getId, Function.identity()));
    }

    /** 엔티티와 건수를 목록 행 DTO로 변환한다. */
    private static SupplierListItem toListItem(Supplier s, SupplierCounts c) {
        AppUser owner = s.getOwner();
        return new SupplierListItem(s.getId(), s.getCode(), s.getName(), s.getStatus(), s.getContactName(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(),
                c.getContractCount(), c.getCertCount(), c.getExpiringCount());
    }

    /** null을 빈 문자열로 바꾼다. (검색 조건 무시 처리용) */
    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    /** 빈 문자열을 null로 바꾸고 앞뒤 공백을 제거한다. */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
