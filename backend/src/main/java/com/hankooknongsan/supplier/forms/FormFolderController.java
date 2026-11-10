package com.hankooknongsan.supplier.forms;

import com.hankooknongsan.supplier.forms.FormFolderDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 폴더 탐색은 로그인 사용자, 쓰기는 기존 API 규칙에 따라 관리자·담당자에게만 허용한다. */
@RestController
@RequestMapping("/api/form-folders")
@RequiredArgsConstructor
public class FormFolderController {
    private final FormFolderService service;

    /** 현재 폴더의 경로와 하위 폴더를 조회한다. */
    @GetMapping("/{id}")
    public FolderView get(@PathVariable Long id) { return service.get(id); }

    /** 부모 아래 새 폴더를 만든다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderItem create(@Valid @RequestBody CreateRequest request) { return service.create(request); }

    /** 폴더 이름을 바꾼다. */
    @PutMapping("/{id}")
    public FolderItem rename(@PathVariable Long id, @Valid @RequestBody RenameRequest request) { return service.rename(id, request); }

    /** 빈 폴더만 삭제하고 삭제자를 남긴다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { service.delete(id, auth.getName()); }
}
