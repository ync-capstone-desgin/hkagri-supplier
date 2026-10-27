package com.hankooknongsan.supplier.file;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 첨부파일 정보 저장소 */
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /** 삭제하지 않은 파일만 ID로 찾는다. */
    Optional<StoredFile> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 아직 삭제하지 않은 파일만 삭제 상태로 바꾸고, 바뀐 행 수(0 또는 1)를 돌려준다.
     * 조회 후 수정하는 대신 조건부 UPDATE 한 번으로 처리해, 두 사람이 동시에 지워도 한 명만 성공하고
     * 처음 삭제한 사람·시각이 덮어써지지 않는다. 자동 기록이 적용되지 않으므로 수정자·수정일시도 함께 넣는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StoredFile f
               set f.deletedAt = :at, f.deletedBy = :by, f.updatedAt = :at, f.updatedBy = :by
             where f.id = :id and f.deletedAt is null""")
    int markDeleted(@Param("id") Long id, @Param("by") String by, @Param("at") LocalDateTime at);

    /** 삭제한 파일을 최근에 지운 순으로 조회한다. (삭제 이력) */
    Page<StoredFile> findByDeletedAtIsNotNullOrderByDeletedAtDesc(Pageable pageable);
}
