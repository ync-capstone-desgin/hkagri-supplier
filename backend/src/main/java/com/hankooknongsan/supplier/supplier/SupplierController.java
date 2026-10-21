package com.hankooknongsan.supplier.supplier;

import java.util.List;

import com.hankooknongsan.supplier.common.ExcelExporter;
import com.hankooknongsan.supplier.common.ExcelExporter.Column;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.supplier.SupplierDtos.BusinessNumberCheck;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierDetail;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierListItem;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 협력업체 API (FR-001~004).
 * 목록·검색, 엑셀 다운로드, 상세, 등록, 수정을 제공한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService service;

    /** 협력업체 목록을 검색한다. 인증 종류(certType)로 보유 업체만 걸러낼 수 있다. 기본 정렬은 업체명. */
    @GetMapping
    public PageResponse<SupplierListItem> search(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) SupplierStatus status,
                                                 @RequestParam(required = false) String certType,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return service.search(keyword, status, certType, PageRequest.of(page, size, Sort.by("name")));
    }

    /** 현재 검색조건의 협력업체 목록을 엑셀 파일로 내려받는다. */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) SupplierStatus status,
                                        @RequestParam(required = false) String certType) {
        List<Column<SupplierListItem>> columns = List.of(
                new Column<>("업체코드", SupplierListItem::code),
                new Column<>("업체명", SupplierListItem::name),
                new Column<>("거래상태", s -> s.status().label()),
                new Column<>("업체 담당자", SupplierListItem::contactName),
                new Column<>("내부 담당자", SupplierListItem::ownerName),
                new Column<>("계약수", SupplierListItem::contractCount),
                new Column<>("인증수", SupplierListItem::certCount),
                new Column<>("만료·임박(90일)", SupplierListItem::expiringCount));
        return ExcelExporter.export("협력업체목록", columns, service.searchAll(keyword, status, certType));
    }

    /** 사업자번호의 검증번호와 중복 등록 여부를 확인한다. 등록·수정 화면이 입력 중에 호출한다. */
    @GetMapping("/business-number-check")
    public BusinessNumberCheck checkBusinessNumber(@RequestParam String number,
                                                   @RequestParam(required = false) Long exceptId) {
        return service.checkBusinessNumber(number, exceptId);
    }

    /** 협력업체 상세정보를 조회한다. */
    @GetMapping("/{id}")
    public SupplierDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 협력업체를 등록한다. 성공 시 201과 등록된 상세정보를 돌려준다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDetail create(@Valid @RequestBody SupplierRequest req) {
        return service.create(req);
    }

    /** 협력업체 기본정보를 수정한다. */
    @PutMapping("/{id}")
    public SupplierDetail update(@PathVariable Long id, @Valid @RequestBody SupplierRequest req) {
        return service.update(id, req);
    }
}
