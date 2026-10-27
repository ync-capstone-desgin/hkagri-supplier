package com.hankooknongsan.supplier.file;

import java.nio.charset.StandardCharsets;

import com.hankooknongsan.supplier.file.FileDtos.Download;
import com.hankooknongsan.supplier.file.FileDtos.FileInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 API (FR-021, FR-022 공통).
 * 올리기·정보 조회·내려받기를 제공한다. 내려받기는 로그인한 사용자 누구나, 올리기는 관리자·담당자만 할 수 있다 (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {

    private final FileService service;

    /** 파일 하나를 올린다. multipart 요청의 "file" 칸으로 보낸다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileInfo upload(@RequestParam("file") MultipartFile file) {
        return service.upload(file);
    }

    /** 파일 정보(이름·크기·올린 사람)를 조회한다. */
    @GetMapping("/{id}")
    public FileInfo get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 파일을 원래 이름으로 내려받는다. 한글 이름도 깨지지 않게 UTF-8로 알려준다. */
    @GetMapping("/{id}/download")
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
}
