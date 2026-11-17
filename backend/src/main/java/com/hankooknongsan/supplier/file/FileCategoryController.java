package com.hankooknongsan.supplier.file;

import java.util.List;

import com.hankooknongsan.supplier.file.FileCategoryDtos.ActiveRequest;
import com.hankooknongsan.supplier.file.FileCategoryDtos.CategoryItem;
import com.hankooknongsan.supplier.file.FileCategoryDtos.NameRequest;
import com.hankooknongsan.supplier.file.FileCategoryDtos.RequiredDocument;
import com.hankooknongsan.supplier.file.FileCategoryDtos.RequiredRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자료 종류 API (#54).
 * 목록은 로그인한 모든 사용자가 보고, 추가·이름 수정·사용 중지는 관리자만 한다 (/api/admin/** 은 보안 설정에서 관리자 전용).
 */
@RestController
@RequiredArgsConstructor
public class FileCategoryController {

    private final FileCategoryService service;

    /** 전체 자료 종류를 표시 순서대로 조회한다. */
    @GetMapping("/api/file-categories")
    public List<CategoryItem> list() {
        return service.list();
    }

    /** 한 업체의 필수 서류별 제출 여부를 조회한다. 로그인한 모든 사용자가 본다. */
    @GetMapping("/api/suppliers/{supplierId}/required-documents")
    public List<RequiredDocument> requiredDocuments(@PathVariable Long supplierId) {
        return service.requiredDocuments(supplierId);
    }

    /** 자료 종류를 필수 서류로 정하거나 해제한다. */
    @PatchMapping("/api/admin/file-categories/{id}/required")
    public CategoryItem changeRequired(@PathVariable Long id, @Valid @RequestBody RequiredRequest request) {
        return service.changeRequired(id, request.required());
    }

    /** 새 자료 종류를 추가한다. */
    @PostMapping("/api/admin/file-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryItem create(@Valid @RequestBody NameRequest request) {
        return service.create(request.name());
    }

    /** 자료 종류의 이름을 바꾼다. */
    @PutMapping("/api/admin/file-categories/{id}")
    public CategoryItem rename(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
        return service.rename(id, request.name());
    }

    /** 자료 종류를 사용 중지하거나 다시 사용한다. */
    @PatchMapping("/api/admin/file-categories/{id}/active")
    public CategoryItem changeActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return service.changeActive(id, request.active());
    }
}
