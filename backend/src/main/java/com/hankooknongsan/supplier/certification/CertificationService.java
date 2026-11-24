package com.hankooknongsan.supplier.certification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.certification.CertificationDtos.CertTypeItem;
import com.hankooknongsan.supplier.certification.CertificationDtos.CertificationItem;
import com.hankooknongsan.supplier.certification.CertificationDtos.CertificationRequest;
import com.hankooknongsan.supplier.certification.CertificationDtos.Saved;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.ExpiryPolicy;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.history.ChangeAction;
import com.hankooknongsan.supplier.history.ChangeLogService;
import com.hankooknongsan.supplier.history.ChangeTarget;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierService;
import lombok.RequiredArgsConstructor;
import com.hankooknongsan.supplier.common.RenewalClosedEvent;
import com.hankooknongsan.supplier.common.TargetDeletedEvent;
import com.hankooknongsan.supplier.common.TargetPurgedEvent;
import com.hankooknongsan.supplier.common.TargetRestoredEvent;
import com.hankooknongsan.supplier.common.TargetType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 등록·조회·수정·갱신 업무 서비스 (FR-006).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificationService {

    private final CertificationRepository certifications;
    private final CertTypeRepository certTypes;
    private final SupplierService supplierService;
    private final AppUserRepository users;
    private final ExpiryPolicy policy;
    private final ApplicationEventPublisher events;
    private final AppClock clock;
    private final ChangeLogService history;

    /** 인증 종류 목록을 표시 순서대로 조회한다. */
    public List<CertTypeItem> certTypes() {
        return certTypes.findAllByOrderBySortOrder().stream()
                .map(CertTypeItem::of)
                .toList();
    }

    /** 조건에 맞는 인증 목록을 조회한다. */
    public PageResponse<CertificationItem> search(Long supplierId, String keyword, String certType, Long ownerId,
                                                  RenewalStatus renewalStatus, boolean includeInactive,
                                                  Pageable pageable) {
        return PageResponse.of(certifications.search(supplierId, keyword == null ? "" : keyword.trim(),
                certType == null ? "" : certType, ownerId, renewalStatus, includeInactive, pageable),
                this::toItem);
    }

    /** 인증 한 건을 조회한다. 없으면 404. */
    public CertificationItem get(Long id) {
        return toItem(find(id));
    }

    /** 인증을 등록한다. 같은 종류·번호의 인증이 이미 있으면 저장은 하되 경고를 돌려준다. */
    @Transactional
    public Saved create(CertificationRequest req) {
        Certification x = new Certification(supplierService.find(req.supplierId()));
        List<String> warnings = apply(x, req, null);
        certifications.save(x);
        log(x, ChangeAction.CREATE, Map.of(), snapshot(x));
        return new Saved(toItem(x), warnings);
    }

    /** 인증 정보를 수정한다. */
    @Transactional
    public Saved update(Long id, CertificationRequest req) {
        Certification x = find(id);
        Map<String, String> before = snapshot(x);
        List<String> warnings = apply(x, req, id);
        certifications.flush();
        log(x, ChangeAction.UPDATE, before, snapshot(x));
        return new Saved(toItem(x), warnings);
    }

    /**
     * 갱신 처리상태(미착수·갱신진행·갱신안함)를 바꾼다. 갱신안함이면 관련 알림을 닫도록 이벤트를 보낸다.
     * 갱신완료는 [갱신] 등록으로만 처리되므로 여기서 지정하면 400, 이미 갱신된 이력 건을 바꾸려 하면 409로 막는다.
     */
    @Transactional
    public CertificationItem changeRenewalStatus(Long id, RenewalStatus status) {
        Certification x = find(id);
        if (status == RenewalStatus.DONE) {
            throw BusinessException.invalid("renewalStatus", "갱신완료는 [갱신]으로 새 인증을 등록해야 처리됩니다.");
        }
        if (!x.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 갱신된 인증은 처리상태를 바꿀 수 없습니다.");
        }
        Map<String, String> before = Map.of("갱신 처리상태", x.getRenewalStatus().label());
        x.changeRenewalStatus(status);
        log(x, ChangeAction.STATUS, before, Map.of("갱신 처리상태", status.label()));
        if (status.closed()) {
            events.publishEvent(new RenewalClosedEvent(TargetType.CERTIFICATION, id));
        }
        certifications.flush();
        return toItem(x);
    }

    /**
     * 인증을 갱신한다.
     * 새 인증을 등록해 이전 인증과 연결하고, 이전 인증은 "갱신완료"·비활성으로 바꾼다.
     * 이미 갱신된(비활성) 인증은 다시 갱신할 수 없다.
     */
    @Transactional
    public Saved renew(Long id, CertificationRequest req) {
        Certification old = find(id);
        if (!old.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 갱신된 인증입니다.");
        }
        Map<String, String> before = snapshot(old);
        old.markRenewed();
        events.publishEvent(new RenewalClosedEvent(TargetType.CERTIFICATION, old.getId()));
        certifications.flush();
        Certification renewed = new Certification(supplierService.find(req.supplierId()));
        List<String> warnings = apply(renewed, req, null);
        renewed.linkPrevious(old);
        certifications.save(renewed);
        log(renewed, ChangeAction.RENEW, before, snapshot(renewed));
        return new Saved(toItem(renewed), warnings);
    }

    /**
     * 인증을 삭제한다 (#44). 지운 표시만 남기고, 알림과 첨부는 이벤트로 함께 정리한다.
     * 갱신으로 등록한 건이면 이전 인증이 다시 현재 인증이 된다. 이전 이력은 직접 지울 수 없어 409로 막는다.
     */
    @Transactional
    public void delete(Long id, String actor) {
        Certification target = find(id);
        if (!target.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "이전 이력은 삭제할 수 없습니다. 현재 인증을 삭제하면 이전 인증이 다시 현재 인증이 됩니다.");
        }
        target.delete(actor, clock.now());
        if (target.getPrev() != null) {
            target.getPrev().restore();
        }
        certifications.flush();
        log(target, ChangeAction.DELETE, Map.of(), Map.of());
        events.publishEvent(new TargetDeletedEvent(TargetType.CERTIFICATION, id, actor));
    }

    /**
     * 삭제한 인증을 되살린다 (#72, 관리자 전용). 삭제할 때 함께 지워진 첨부도 되살린다.
     * 갱신으로 등록했던 건이면, 삭제 때 다시 현재 건이 됐던 이전 인증을 "갱신완료" 이력으로 돌려놓는다.
     * 협력업체가 삭제된 상태이거나 이전 인증이 그 사이 다시 갱신·삭제되었으면 409로 막는다.
     */
    @Transactional
    public void restore(Long id) {
        if (certifications.countDeleted(id) == 0) {
            throw BusinessException.notFound("삭제된 인증");
        }
        if (certifications.countDeletedSupplier(id) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "협력업체가 삭제된 상태입니다. 협력업체를 먼저 되살려 주세요.");
        }
        if (certifications.countUnavailablePrev(id) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "이전 인증이 그 사이 다시 갱신되었거나 삭제되어 되살릴 수 없습니다. 이전 인증을 현재 인증으로 돌려놓은 뒤 다시 시도해 주세요.");
        }
        // 파일은 인증의 지운 시각과 비교해 되살리므로 지운 표시를 지우기 전에 알린다.
        events.publishEvent(new TargetRestoredEvent(TargetType.CERTIFICATION, id));
        certifications.undelete(id);
        Certification target = find(id);
        if (target.getPrev() != null) {
            target.getPrev().markRenewed();
            events.publishEvent(new RenewalClosedEvent(TargetType.CERTIFICATION, target.getPrev().getId()));
        }
        log(target, ChangeAction.RESTORE, Map.of(), Map.of());
    }

    /**
     * 삭제된 인증을 영구삭제한다 (#77, 관리자 전용). 되돌릴 수 없다.
     * 첨부는 이벤트로 DB와 저장소에서 함께 지우고, 영구삭제했다는 사실만 변경 이력에 남긴다.
     */
    @Transactional
    public void purge(Long id) {
        if (certifications.countDeleted(id) == 0) {
            throw BusinessException.notFound("삭제된 인증");
        }
        events.publishEvent(new TargetPurgedEvent(TargetType.CERTIFICATION, id));
        certifications.detachPrev(id);
        certifications.purge(id);
        history.recordPurge(ChangeTarget.CERTIFICATION, id);
    }

    /** 인증의 변경 이력을 남긴다 (#45). */
    private void log(Certification x, ChangeAction action, Map<String, String> before, Map<String, String> after) {
        history.record(ChangeTarget.CERTIFICATION, x.getId(), x.getSupplier().getId(), x.getSupplier().getName(),
                x.getDisplayName(), action, before, after);
    }

    /** 변경 이력 비교용으로 인증의 현재 값을 화면 항목 이름과 함께 뽑는다. */
    private static Map<String, String> snapshot(Certification x) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("업체", x.getSupplier().getName());
        m.put("인증 종류", x.getCertType().getName());
        m.put("인증번호", x.getCertificateNumber());
        m.put("적용 대상", x.getScope());
        m.put("발급일", String.valueOf(x.getIssuedDate()));
        m.put("만료일", x.getExpiryDate() == null ? "없음" : x.getExpiryDate().toString());
        m.put("담당자", x.getOwner() == null ? null : x.getOwner().getName());
        m.put("문서 링크", x.getDocumentUrl());
        m.put("비고", x.getMemo());
        return m;
    }

    /** ID로 인증 엔티티를 찾는다. */
    public Certification find(Long id) {
        return certifications.findById(id).orElseThrow(() -> BusinessException.notFound("인증"));
    }

    /**
     * 요청 값을 검증해 엔티티에 반영하고 경고 목록을 돌려준다.
     * <ul>
     *   <li>만료일이 발급일보다 빠르면 저장하지 않는다</li>
     *   <li>담당자를 지정하지 않으면 업체의 내부 담당자를 그대로 쓴다</li>
     *   <li>같은 종류·인증번호가 이미 있으면 경고한다</li>
     * </ul>
     */
    private List<String> apply(Certification x, CertificationRequest req, Long exceptId) {
        if (req.expiryDate() != null && req.expiryDate().isBefore(req.issuedDate())) {
            throw BusinessException.invalid("expiryDate", "만료일은 발급일보다 빠를 수 없습니다.");
        }
        Supplier supplier = supplierService.find(req.supplierId());
        CertType type = certTypes.findForSelection(req.certTypeCode())
                .orElseThrow(() -> BusinessException.invalid("certTypeCode", "인증 종류를 찾을 수 없습니다."));
        boolean keepingType = exceptId != null && x.getCertType().getCode().equals(type.getCode());
        if (!type.isActive() && !keepingType) {
            throw BusinessException.invalid("certTypeCode", "비활성 인증 종류입니다. 사용 중인 종류를 선택해 주세요.");
        }
        AppUser owner = req.ownerId() != null
                ? users.findById(req.ownerId()).orElseThrow(() -> BusinessException.invalid("ownerId", "담당자를 찾을 수 없습니다."))
                : supplier.getOwner();
        String number = req.certificateNumber().trim();

        List<String> warnings = new ArrayList<>();
        if (certifications.existsDuplicate(type.getCode(), number, exceptId)) {
            warnings.add(type.getName() + " 인증번호 " + number + "이(가) 이미 등록돼 있습니다. 번호를 확인해 주세요.");
        }
        x.update(supplier, type, number, blankToNull(req.scope()), req.issuedDate(), req.expiryDate(), owner,
                blankToNull(req.documentUrl()), blankToNull(req.memo()));
        return warnings;
    }

    /** 인증 엔티티를 오늘 기준 D-day가 포함된 응답 형식으로 변환한다. */
    CertificationItem toItem(Certification x) {
        return CertificationItem.of(x, policy);
    }

    /** 빈 문자열을 null로 바꾸고 앞뒤 공백을 제거한다. */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
