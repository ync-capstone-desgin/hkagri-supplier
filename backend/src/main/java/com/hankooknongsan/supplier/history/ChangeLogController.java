package com.hankooknongsan.supplier.history;

import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.history.ChangeLogDtos.ChangeItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 변경 이력 조회 API (#45).
 * 업체별 이력은 로그인한 모든 사용자가, 전체 이력은 관리자만 본다 (/api/admin/** 은 보안 설정에서 관리자 전용).
 */
@RestController
@RequiredArgsConstructor
public class ChangeLogController {

    private final ChangeLogService service;

    /** 한 업체와 그 업체의 계약·인증 변경 이력을 최신순으로 조회한다. */
    @GetMapping("/api/suppliers/{supplierId}/changes")
    public PageResponse<ChangeItem> forSupplier(@PathVariable Long supplierId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return service.forSupplier(supplierId, PageRequest.of(page, size));
    }

    /** 전체 변경 이력을 대상 종류와 검색어(업체명·대상 이름·작업자)로 검색한다. */
    @GetMapping("/api/admin/changes")
    public PageResponse<ChangeItem> search(@RequestParam(required = false) ChangeTarget targetType,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return service.search(targetType, keyword, PageRequest.of(page, size));
    }
}
