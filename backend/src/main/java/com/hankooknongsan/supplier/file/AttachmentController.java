package com.hankooknongsan.supplier.file;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hankooknongsan.supplier.file.FileDtos.FileInfo;
import com.hankooknongsan.supplier.file.StoredFileRepository.IdCount;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계약·인증 건에 달린 첨부파일 조회 API (FR-021, FR-012).
 * 올리기·내려받기·삭제는 기존 공용 API({@link FileController}, {@link SupplierFileController})를 그대로 쓴다.
 */
@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final StoredFileRepository files;

    /** 계약 한 건에 연결된 첨부파일 목록 (최신순). */
    @GetMapping("/api/contracts/{contractId}/files")
    public List<FileInfo> forContract(@PathVariable Long contractId) {
        return files.findByContractIdAndDeletedAtIsNullOrderByCreatedAtDesc(contractId).stream().map(FileInfo::of).toList();
    }

    /** 인증 한 건에 연결된 첨부파일 목록 (최신순). */
    @GetMapping("/api/certifications/{certificationId}/files")
    public List<FileInfo> forCertification(@PathVariable Long certificationId) {
        return files.findByCertificationIdAndDeletedAtIsNullOrderByCreatedAtDesc(certificationId).stream().map(FileInfo::of).toList();
    }

    /** 목록 화면에서 첨부 아이콘을 한 번에 그리기 위한, 계약별 첨부 개수. */
    @GetMapping("/api/contracts/files/counts")
    public Map<Long, Long> contractCounts(@RequestParam List<Long> ids) {
        return toCountMap(files.countByContractIds(ids));
    }

    /** 목록 화면에서 첨부 아이콘을 한 번에 그리기 위한, 인증별 첨부 개수. */
    @GetMapping("/api/certifications/files/counts")
    public Map<Long, Long> certificationCounts(@RequestParam List<Long> ids) {
        return toCountMap(files.countByCertificationIds(ids));
    }

    private Map<Long, Long> toCountMap(List<IdCount> rows) {
        return rows.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getCount));
    }
}
