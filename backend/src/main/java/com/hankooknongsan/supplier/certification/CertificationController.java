package com.hankooknongsan.supplier.certification;

import java.util.List;

import com.hankooknongsan.supplier.certification.CertificationDtos.CertTypeItem;
import com.hankooknongsan.supplier.certification.CertificationDtos.CertificationItem;
import com.hankooknongsan.supplier.certification.CertificationDtos.CertificationRequest;
import com.hankooknongsan.supplier.certification.CertificationDtos.Saved;
import com.hankooknongsan.supplier.common.ExcelExporter;
import com.hankooknongsan.supplier.common.ExcelExporter.Column;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.common.RenewalStatusRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (FR-006).
 * 인증 종류 목록, 인증 목록·검색, 엑셀, 상세, 등록, 수정, 갱신, 갱신 처리상태 변경을 제공한다.
 */
@RestController
@RequiredArgsConstructor
public class CertificationController {

    /** 기본 정렬: 만료일이 가까운 순 (만료일 없음은 맨 뒤) */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("expiryDate").nullsLast(), Sort.Order.asc("id"));

    private final CertificationService service;

    /** 인증 종류 목록을 조회한다. (선택상자용) */
    @GetMapping("/api/cert-types")
    public List<CertTypeItem> certTypes() {
        return service.certTypes();
    }

    /** 인증 목록을 검색한다. supplierId를 주면 해당 업체의 인증만 조회한다 (업체 상세 화면용). */
    @GetMapping("/api/certifications")
    public PageResponse<CertificationItem> search(@RequestParam(required = false) Long supplierId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String certType,
                                                  @RequestParam(required = false) Long ownerId,
                                                  @RequestParam(required = false) RenewalStatus renewalStatus,
                                                  @RequestParam(defaultValue = "false") boolean includeInactive,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "500") int size) {
        return service.search(supplierId, keyword, certType, ownerId, renewalStatus, includeInactive,
                PageRequest.of(page, size, DEFAULT_SORT));
    }

    /** 현재 검색조건의 인증 목록을 엑셀 파일로 내려받는다. */
    @GetMapping("/api/certifications/excel")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) Long supplierId,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String certType,
                                        @RequestParam(required = false) Long ownerId,
                                        @RequestParam(required = false) RenewalStatus renewalStatus,
                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        List<CertificationItem> rows = service.search(supplierId, keyword, certType, ownerId, renewalStatus,
                includeInactive, Pageable.ofSize(10_000).withPage(0)).content();
        List<Column<CertificationItem>> columns = List.of(
                new Column<>("업체명", CertificationItem::supplierName),
                new Column<>("인증 종류", CertificationItem::certTypeName),
                new Column<>("인증번호", CertificationItem::certificateNumber),
                new Column<>("발급일", CertificationItem::issuedDate),
                new Column<>("만료일", x -> x.expiryDate() == null ? "없음" : x.expiryDate()),
                new Column<>("갱신 처리상태", x -> x.renewalStatus().label()),
                new Column<>("담당자", CertificationItem::ownerName));
        return ExcelExporter.export("인증목록", columns, rows);
    }

    /** 인증 한 건을 조회한다. */
    @GetMapping("/api/certifications/{id}")
    public CertificationItem get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 인증을 등록한다. */
    @PostMapping("/api/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    public Saved create(@Valid @RequestBody CertificationRequest req) {
        return service.create(req);
    }

    /** 인증 정보를 수정한다. */
    @PutMapping("/api/certifications/{id}")
    public Saved update(@PathVariable Long id, @Valid @RequestBody CertificationRequest req) {
        return service.update(id, req);
    }

    /** 인증을 갱신한다: 새 인증을 등록하고 기존 인증은 갱신완료·비활성으로 바꾼다. */
    @PostMapping("/api/certifications/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    public Saved renew(@PathVariable Long id, @Valid @RequestBody CertificationRequest req) {
        return service.renew(id, req);
    }

    /** 갱신 처리상태만 바꾼다. (목록에서 바로 변경할 때 사용) */
    @PatchMapping("/api/certifications/{id}/renewal-status")
    public CertificationItem changeRenewalStatus(@PathVariable Long id, @Valid @RequestBody RenewalStatusRequest req) {
        return service.changeRenewalStatus(id, req.renewalStatus());
    }
}
