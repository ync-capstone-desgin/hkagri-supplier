package com.hankooknongsan.supplier.forms;

import java.util.List;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.forms.FormDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 사내 양식 자료실 API. 읽기는 로그인 사용자, 쓰기는 ADMIN·MANAGER(SecurityConfig)다. */
@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {
    private final FormService service;

    /** 제목·분류로 게시글을 검색한다. */
    @GetMapping
    public PageResponse<PostItem> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category, @RequestParam(required = false) Long folderId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, category, folderId, page, size);
    }

    /** 게시글 상세와 첨부 정보를 조회한다. */
    @GetMapping("/{id}")
    public PostDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    /** JSON post와 여러 files를 함께 저장한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetail create(@Valid @RequestPart("post") PostRequest post,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.create(post, files);
    }

    /** 게시글 수정과 첨부 교체를 함께 반영한다. */
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostDetail update(@PathVariable Long id, @Valid @RequestPart("post") PostRequest post,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, Authentication auth) {
        return service.update(id, post, files, auth.getName());
    }

    /** 게시글과 첨부를 삭제하고 감사 이력을 보존한다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth.getName());
    }
}
