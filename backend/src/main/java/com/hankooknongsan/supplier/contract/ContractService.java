package com.hankooknongsan.supplier.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.ExpiryPolicy;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.history.ChangeAction;
import com.hankooknongsan.supplier.history.ChangeLogService;
import com.hankooknongsan.supplier.history.ChangeTarget;
import com.hankooknongsan.supplier.contract.ContractDtos.ContractItem;
import com.hankooknongsan.supplier.contract.ContractDtos.ContractRequest;
import com.hankooknongsan.supplier.contract.ContractDtos.Saved;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierService;
import lombok.RequiredArgsConstructor;
import com.hankooknongsan.supplier.common.RenewalClosedEvent;
import com.hankooknongsan.supplier.common.TargetDeletedEvent;
import com.hankooknongsan.supplier.common.TargetType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 등록·조회·수정·갱신 업무 서비스 (FR-005).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractService {

    private final ContractRepository contracts;
    private final SupplierService supplierService;
    private final AppUserRepository users;
    private final ExpiryPolicy policy;
    private final ApplicationEventPublisher events;
    private final AppClock clock;
    private final ChangeLogService history;

    /** 조건에 맞는 계약 목록을 조회한다. */
    public PageResponse<ContractItem> search(Long supplierId, String keyword, Long ownerId,
                                             RenewalStatus renewalStatus, boolean includeInactive, Pageable pageable) {
        return PageResponse.of(contracts.search(supplierId, keyword == null ? "" : keyword.trim(), ownerId,
                renewalStatus, includeInactive, pageable), this::toItem);
    }

    /** 계약 한 건을 조회한다. 없으면 404. */
    public ContractItem get(Long id) {
        return toItem(find(id));
    }

    /** 계약을 등록한다. 계약번호가 같은 업체에 이미 있으면 저장은 하되 경고를 돌려준다. */
    @Transactional
    public Saved create(ContractRequest req) {
        Contract c = new Contract(supplierService.find(req.supplierId()));
        List<String> warnings = apply(c, req, null);
        contracts.save(c);
        log(c, ChangeAction.CREATE, Map.of(), snapshot(c));
        return new Saved(toItem(c), warnings);
    }

    /** 계약 정보를 수정한다. */
    @Transactional
    public Saved update(Long id, ContractRequest req) {
        Contract c = find(id);
        Map<String, String> before = snapshot(c);
        List<String> warnings = apply(c, req, id);
        contracts.flush();
        log(c, ChangeAction.UPDATE, before, snapshot(c));
        return new Saved(toItem(c), warnings);
    }

    /**
     * 갱신 처리상태(미착수·갱신진행·갱신안함)를 바꾼다. 갱신안함이면 관련 알림을 닫도록 이벤트를 보낸다.
     * 갱신완료는 [갱신] 등록으로만 처리되므로 여기서 지정하면 400, 이미 갱신된 이력 건을 바꾸려 하면 409로 막는다.
     */
    @Transactional
    public ContractItem changeRenewalStatus(Long id, RenewalStatus status) {
        Contract c = find(id);
        if (status == RenewalStatus.DONE) {
            throw BusinessException.invalid("renewalStatus", "갱신완료는 [갱신]으로 새 계약을 등록해야 처리됩니다.");
        }
        if (!c.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 갱신된 계약은 처리상태를 바꿀 수 없습니다.");
        }
        Map<String, String> before = Map.of("갱신 처리상태", c.getRenewalStatus().label());
        c.changeRenewalStatus(status);
        log(c, ChangeAction.STATUS, before, Map.of("갱신 처리상태", status.label()));
        if (status.closed()) {
            events.publishEvent(new RenewalClosedEvent(TargetType.CONTRACT, id));
        }
        contracts.flush();
        return toItem(c);
    }

    /**
     * 계약을 갱신한다.
     * 새 계약을 등록해 이전 계약과 연결하고, 이전 계약은 "갱신완료"·비활성으로 바꾼다.
     * 이미 갱신된(비활성) 계약은 다시 갱신할 수 없다.
     */
    @Transactional
    public Saved renew(Long id, ContractRequest req) {
        Contract old = find(id);
        if (!old.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 갱신된 계약입니다.");
        }
        Map<String, String> before = snapshot(old);
        old.markRenewed();
        events.publishEvent(new RenewalClosedEvent(TargetType.CONTRACT, old.getId()));
        contracts.flush();
        Contract renewed = new Contract(supplierService.find(req.supplierId()));
        List<String> warnings = apply(renewed, req, null);
        renewed.linkPrevious(old);
        contracts.save(renewed);
        log(renewed, ChangeAction.RENEW, before, snapshot(renewed));
        return new Saved(toItem(renewed), warnings);
    }

    /**
     * 계약을 삭제한다 (#44). 지운 표시만 남기고, 알림과 첨부는 이벤트로 함께 정리한다.
     * 갱신으로 등록한 건이면 이전 계약이 다시 현재 계약이 된다. 이전 이력은 직접 지울 수 없어 409로 막는다.
     */
    @Transactional
    public void delete(Long id, String actor) {
        Contract target = find(id);
        if (!target.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "이전 이력은 삭제할 수 없습니다. 현재 계약을 삭제하면 이전 계약이 다시 현재 계약이 됩니다.");
        }
        target.delete(actor, clock.now());
        if (target.getPrev() != null) {
            target.getPrev().restore();
        }
        contracts.flush();
        log(target, ChangeAction.DELETE, Map.of(), Map.of());
        events.publishEvent(new TargetDeletedEvent(TargetType.CONTRACT, id, actor));
    }

    /** 계약의 변경 이력을 남긴다 (#45). */
    private void log(Contract c, ChangeAction action, Map<String, String> before, Map<String, String> after) {
        history.record(ChangeTarget.CONTRACT, c.getId(), c.getSupplier().getId(), c.getSupplier().getName(),
                c.getTitle(), action, before, after);
    }

    /** 변경 이력 비교용으로 계약의 현재 값을 화면 항목 이름과 함께 뽑는다. */
    private static Map<String, String> snapshot(Contract c) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("업체", c.getSupplier().getName());
        m.put("계약번호", c.getContractNumber() == null || c.getContractNumber().isEmpty() ? null : c.getContractNumber());
        m.put("계약명", c.getTitle());
        m.put("시작일", String.valueOf(c.getStartDate()));
        m.put("종료일", c.getEndDate() == null ? "무기한" : c.getEndDate().toString());
        m.put("자동연장", c.isAutoRenew() ? "예" : "아니오");
        m.put("담당자", c.getOwner() == null ? null : c.getOwner().getName());
        m.put("문서 링크", c.getDocumentUrl());
        m.put("비고", c.getMemo());
        return m;
    }

    /** ID로 계약 엔티티를 찾는다. */
    public Contract find(Long id) {
        return contracts.findById(id).orElseThrow(() -> BusinessException.notFound("계약"));
    }

    /**
     * 요청 값을 검증해 엔티티에 반영하고 경고 목록을 돌려준다.
     * <ul>
     *   <li>종료일이 시작일보다 빠르면 저장하지 않는다 (VT-003)</li>
     *   <li>담당자를 지정하지 않으면 업체의 내부 담당자를 그대로 쓴다</li>
     *   <li>같은 업체에 같은 계약번호가 있으면 경고한다</li>
     * </ul>
     */
    private List<String> apply(Contract c, ContractRequest req, Long exceptId) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw BusinessException.invalid("endDate", "종료일은 시작일보다 빠를 수 없습니다.");
        }
        Supplier supplier = supplierService.find(req.supplierId());
        AppUser owner = req.ownerId() != null
                ? users.findById(req.ownerId()).orElseThrow(() -> BusinessException.invalid("ownerId", "담당자를 찾을 수 없습니다."))
                : supplier.getOwner();
        String number = req.contractNumber() == null ? "" : req.contractNumber().trim();

        List<String> warnings = new ArrayList<>();
        if (!number.isEmpty() && contracts.existsDuplicate(supplier.getId(), number, exceptId)) {
            warnings.add("같은 업체에 계약번호 " + number + "이(가) 이미 있습니다. 번호를 확인해 주세요.");
        }
        c.update(supplier, number, req.title().trim(), req.startDate(), req.endDate(), Boolean.TRUE.equals(req.autoRenew()), owner,
                blankToNull(req.documentUrl()), blankToNull(req.memo()));
        return warnings;
    }

    /** 계약 엔티티를 오늘 기준 D-day가 포함된 응답 형식으로 변환한다. */
    ContractItem toItem(Contract c) {
        return ContractItem.of(c, policy);
    }

    /** 빈 문자열을 null로 바꾸고 앞뒤 공백을 제거한다. */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
