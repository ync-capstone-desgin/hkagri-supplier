package com.hankooknongsan.supplier.file;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 첨부파일 정보 저장소 */
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /** 삭제하지 않은 파일만 ID로 찾는다. */
    Optional<StoredFile> findByIdAndDeletedAtIsNull(Long id);

    /** 삭제한 파일을 최근에 지운 순으로 조회한다. (삭제 이력) */
    Page<StoredFile> findByDeletedAtIsNotNullOrderByDeletedAtDesc(Pageable pageable);
}
