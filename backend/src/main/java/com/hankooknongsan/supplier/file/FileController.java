package com.hankooknongsan.supplier.file;

import java.nio.charset.StandardCharsets;

import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.file.FileDtos.DeletedFile;
import com.hankooknongsan.supplier.file.FileDtos.Download;
import com.hankooknongsan.supplier.file.FileDtos.FileInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 API (FR-021, FR-022 공통).
 * 올리기·정보 조회·내려받기·삭제와 관리자용 삭제 이력을 제공한다.
 * 권한은 SecurityConfig 규칙을 따른다: 조회·내려받기는 로그인한 사용자 누구나, 올리기·삭제는 관리자·담당자, 삭제 이력은 관리자만.
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileService service;

    /** 파일 하나를 올린다. multipart 요청의 "file" 칸으로 보낸다. */
    @PostMapping(path = "/api/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileInfo upload(@RequestParam("file") MultipartFile file) {
        return service.upload(file);
    }

    /** 파일 정보(이름·크기·올린 사람)를 조회한다. */
    @GetMapping("/api/files/{id}")
    public FileInfo get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 파일을 원래 이름으로 내려받는다. 한글 이름도 깨지지 않게 UTF-8로 알려준다. */
    @GetMapping("/api/files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Download d = service.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(d.info().originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(d.info().contentType()))
                .contentLength(d.info().size())
                .body(d.content());
    }

    /** 파일을 삭제한다. 삭제한 사람으로 현재 로그인 사용자를 기록한다. */
    @DeleteMapping("/api/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth.getName());
    }

    /** 삭제한 파일 목록(삭제 이력)을 최근에 지운 순으로 조회한다. (관리자 전용) */
    @GetMapping("/api/admin/files/deleted")
    public PageResponse<DeletedFile> deletedHistory(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return service.deletedHistory(PageRequest.of(page, size));
    }
}
