package com.hankooknongsan.supplier.certification;

import com.hankooknongsan.supplier.certification.CertificationDtos.CertTypeItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/admin/**의 ADMIN 권한 정책을 적용하는 인증 종류 변경 API. */
@RestController
@RequestMapping("/api/admin/cert-types")
@RequiredArgsConstructor
public class CertTypeController {
    private final CertTypeService service;

    /** 관리자만 새 인증 종류를 추가할 수 있다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertTypeItem create(@Valid @RequestBody CertTypeDtos.CreateRequest request) {
        return service.create(request);
    }

    /** 사용 여부와 독립적으로 이름을 수정한다. */
    @PutMapping("/{code}")
    public CertTypeItem update(@PathVariable String code, @Valid @RequestBody CertTypeDtos.UpdateRequest request) {
        return service.update(code, request);
    }

    /** 기존 인증 기록은 유지하고 신규 선택 가능 여부를 변경한다. */
    @PatchMapping("/{code}/active")
    public CertTypeItem changeActive(@PathVariable String code, @Valid @RequestBody CertTypeDtos.ActiveRequest request) {
        return service.changeActive(code, request.active());
    }
}
