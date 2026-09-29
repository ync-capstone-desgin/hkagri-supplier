package com.hankooknongsan.supplier.contract;

import java.util.List;

import com.hankooknongsan.supplier.common.ExcelExporter;
import com.hankooknongsan.supplier.common.ExcelExporter.Column;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.common.RenewalStatusRequest;
import com.hankooknongsan.supplier.contract.ContractDtos.ContractItem;
import com.hankooknongsan.supplier.contract.ContractDtos.ContractRequest;
import com.hankooknongsan.supplier.contract.ContractDtos.Saved;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계약 API (FR-005).
 * 목록·검색, 엑셀, 상세, 등록, 수정, 갱신, 갱신 처리상태 변경을 제공한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts")
public class ContractController {

    /** 기본 정렬: 종료일이 가까운 순 (무기한 계약은 맨 뒤) */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("endDate").nullsLast(), Sort.Order.asc("id"));

    private final ContractService service;

    /** 계약 목록을 검색한다. supplierId를 주면 해당 업체의 계약만 조회한다 (업체 상세 화면용). */
    @GetMapping
    public PageResponse<ContractItem> search(@RequestParam(required = false) Long supplierId,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long ownerId,
                                             @RequestParam(required = false) RenewalStatus renewalStatus,
                                             @RequestParam(defaultValue = "false") boolean includeInactive,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "500") int size) {
        return service.search(supplierId, keyword, ownerId, renewalStatus, includeInactive,
                PageRequest.of(page, size, DEFAULT_SORT));
    }

    /** 현재 검색조건의 계약 목록을 엑셀 파일로 내려받는다. */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) Long supplierId,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long ownerId,
                                        @RequestParam(required = false) RenewalStatus renewalStatus,
                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        List<ContractItem> rows = service.search(supplierId, keyword, ownerId, renewalStatus, includeInactive,
                Pageable.ofSize(10_000).withPage(0)).content();
        List<Column<ContractItem>> columns = List.of(
                new Column<>("업체명", ContractItem::supplierName),
                new Column<>("계약번호", ContractItem::contractNumber),
                new Column<>("계약명", ContractItem::title),
                new Column<>("시작일", ContractItem::startDate),
                new Column<>("종료일", c -> c.endDate() == null ? "무기한" : c.endDate()),
                new Column<>("자동연장", c -> c.autoRenew() ? "예" : "아니오"),
                new Column<>("갱신 처리상태", c -> c.renewalStatus().label()),
                new Column<>("담당자", ContractItem::ownerName));
        return ExcelExporter.export("계약목록", columns, rows);
    }

    /** 계약 한 건을 조회한다. */
    @GetMapping("/{id}")
    public ContractItem get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 계약을 등록한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Saved create(@Valid @RequestBody ContractRequest req) {
        return service.create(req);
    }

    /** 계약 정보를 수정한다. */
    @PutMapping("/{id}")
    public Saved update(@PathVariable Long id, @Valid @RequestBody ContractRequest req) {
        return service.update(id, req);
    }

    /** 계약을 갱신한다: 새 계약을 등록하고 기존 계약은 갱신완료·비활성으로 바꾼다. */
    @PostMapping("/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    public Saved renew(@PathVariable Long id, @Valid @RequestBody ContractRequest req) {
        return service.renew(id, req);
    }

    /** 갱신 처리상태만 바꾼다. (목록에서 바로 변경할 때 사용) */
    @PatchMapping("/{id}/renewal-status")
    public ContractItem changeRenewalStatus(@PathVariable Long id, @Valid @RequestBody RenewalStatusRequest req) {
        return service.changeRenewalStatus(id, req.renewalStatus());
    }
}
