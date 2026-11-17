package com.hankooknongsan.supplier.supplier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.ExpiryPolicy;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.common.SupplierDeletedEvent;
import com.hankooknongsan.supplier.common.SupplierRestoredEvent;
import com.hankooknongsan.supplier.history.ChangeAction;
import com.hankooknongsan.supplier.history.ChangeLogService;
import com.hankooknongsan.supplier.history.ChangeTarget;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierDetail;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierListItem;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierRequest;
import com.hankooknongsan.supplier.supplier.SupplierRepository.SupplierCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협력업체 등록·조회·수정·검색 업무 서비스 (FR-001~004).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;
    private final AppUserRepository users;
    private final ExpiryPolicy policy;
    private final AppClock clock;
    private final ApplicationEventPublisher events;
    private final ChangeLogService history;

    /** 조건에 맞는 협력업체 목록을 페이지 단위로 조회한다. missingDocs가 true면 필수 서류가 빠진 업체만, certExpired가 true면 필수 인증이 만료된 업체만 조회한다. */
    public PageResponse<SupplierListItem> search(String keyword, SupplierStatus status, String certType,
                                                 boolean missingDocs, boolean certExpired, Pageable pageable) {
        Page<Supplier> page = suppliers.search(nullToEmpty(keyword).trim(), status, nullToEmpty(certType), missingDocs,
                certExpired, policy.today(), pageable);
        Map<Long, SupplierCounts> counts = countsOf(page.getContent());
        return PageResponse.of(page, s -> toListItem(s, counts.get(s.getId())));
    }

    /** 조건에 맞는 협력업체 전체를 조회한다. (엑셀 다운로드용) */
    public List<SupplierListItem> searchAll(String keyword, SupplierStatus status, String certType,
                                            boolean missingDocs, boolean certExpired) {
        return search(keyword, status, certType, missingDocs, certExpired, Pageable.ofSize(10_000)).content();
    }

    /** 협력업체 한 곳의 상세정보를 조회한다. 없으면 404. */
    public SupplierDetail get(Long id) {
        Supplier s = find(id);
        SupplierCounts c = countsOf(List.of(s)).get(id);
        AppUser owner = s.getOwner();
        return new SupplierDetail(s.getId(), s.getCode(), s.getName(), s.getBusinessNumber(), s.getStatus(),
                s.getContactName(), s.getContactPhone(), s.getContactEmail(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(), s.getMemo(),
                c.getContractCount(), c.getCertCount(), c.getExpiringCount(), c.getMissingDocCount(),
                c.getExpiredRequiredCertCount(), s.getCreatedAt(), s.getCreatedBy(), s.getUpdatedAt(), s.getUpdatedBy());
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
        log(s, ChangeAction.CREATE, Map.of(), snapshot(s));
        return get(s.getId());
    }

    /** 협력업체 기본정보를 수정한다. */
    @Transactional
    public SupplierDetail update(Long id, SupplierRequest req) {
        Supplier s = find(id);
        Map<String, String> before = snapshot(s);
        apply(s, req);
        suppliers.flush();
        log(s, ChangeAction.UPDATE, before, snapshot(s));
        return get(id);
    }

    /**
     * 협력업체를 삭제한다 (#44). 지운 표시만 남긴다.
     * 계약·인증(이전 이력 포함)이 남아 있으면 409로 막고, 업체에 올린 일반 자료는 이벤트로 함께 삭제한다.
     */
    @Transactional
    public void delete(Long id, String actor) {
        Supplier s = find(id);
        long contracts = suppliers.countContracts(id);
        long certifications = suppliers.countCertifications(id);
        if (contracts + certifications > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "계약 %d건, 인증 %d건이 남아 있어 삭제할 수 없습니다. 계약·인증을 먼저 삭제해 주세요."
                            .formatted(contracts, certifications));
        }
        s.delete(actor, clock.now());
        suppliers.flush();
        log(s, ChangeAction.DELETE, Map.of(), Map.of());
        events.publishEvent(new SupplierDeletedEvent(id, actor));
    }

    /** 삭제한 협력업체를 되살린다 (#72, 관리자 전용). 삭제할 때 함께 지워진 일반 자료도 되살린다. */
    @Transactional
    public void restore(Long id) {
        if (suppliers.countDeleted(id) == 0) {
            throw BusinessException.notFound("삭제된 협력업체");
        }
        // 자료는 업체의 지운 시각과 비교해 되살리므로 지운 표시를 지우기 전에 알린다.
        events.publishEvent(new SupplierRestoredEvent(id));
        suppliers.undelete(id);
        log(find(id), ChangeAction.RESTORE, Map.of(), Map.of());
    }

    /** 협력업체의 변경 이력을 남긴다 (#45). */
    private void log(Supplier s, ChangeAction action, Map<String, String> before, Map<String, String> after) {
        history.record(ChangeTarget.SUPPLIER, s.getId(), s.getId(), s.getName(), s.getName(), action, before, after);
    }

    /** 변경 이력 비교용으로 협력업체의 현재 값을 화면 항목 이름과 함께 뽑는다. */
    private static Map<String, String> snapshot(Supplier s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("업체명", s.getName());
        m.put("사업자번호", s.getBusinessNumber());
        m.put("거래상태", s.getStatus().label());
        m.put("업체 담당자", s.getContactName());
        m.put("연락처", s.getContactPhone());
        m.put("이메일", s.getContactEmail());
        m.put("내부 담당자", s.getOwner() == null ? null : s.getOwner().getName());
        m.put("비고", s.getMemo());
        return m;
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

    /**
     * 업체들의 계약·인증 건수를 한 번에 조회해 업체 ID별로 묶는다.
     * "만료건수"는 만료 판정 규칙과 같은 기준(이미 만료 + 만료예정 기간 이내)을 쓴다.
     */
    private Map<Long, SupplierCounts> countsOf(List<Supplier> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return suppliers.counts(list.stream().map(Supplier::getId).toList(),
                        policy.horizon(), policy.today())
                .stream().collect(Collectors.toMap(SupplierCounts::getId, Function.identity()));
    }

    /** 엔티티와 건수를 목록 행 DTO로 변환한다. */
    private static SupplierListItem toListItem(Supplier s, SupplierCounts c) {
        AppUser owner = s.getOwner();
        return new SupplierListItem(s.getId(), s.getCode(), s.getName(), s.getStatus(), s.getContactName(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(),
                c.getContractCount(), c.getCertCount(), c.getExpiringCount(), c.getMissingDocCount(),
                c.getExpiredRequiredCertCount());
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
