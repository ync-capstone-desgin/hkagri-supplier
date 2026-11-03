package com.hankooknongsan.supplier.file;

import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.file.FileDtos.SupplierFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 업체 자료 탭 API. 조회는 로그인 사용자, 업로드는 관리자·담당자만 허용한다(SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class SupplierFileController {
    private final SupplierFileService service;

    /** 업체의 자료를 페이지별로 조회한다. */
    @GetMapping("/api/suppliers/{supplierId}/files")
    public PageResponse<SupplierFile> list(@PathVariable Long supplierId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(supplierId, page, size);
    }

    /** 파일 하나를 업체 또는 업체의 계약·인증에 연결한다. 다중 업로드는 화면에서 파일별로 요청한다. */
    @PostMapping(path = "/api/suppliers/{supplierId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierFile upload(@PathVariable Long supplierId, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long contractId, @RequestParam(required = false) Long certificationId) {
        return service.upload(supplierId, file, contractId, certificationId);
    }
}
