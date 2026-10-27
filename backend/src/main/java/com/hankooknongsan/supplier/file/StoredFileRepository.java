package com.hankooknongsan.supplier.file;

import org.springframework.data.jpa.repository.JpaRepository;

/** 첨부파일 정보 저장소 */
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
}
