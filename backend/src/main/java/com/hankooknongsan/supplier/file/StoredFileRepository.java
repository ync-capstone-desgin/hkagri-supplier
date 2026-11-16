package com.hankooknongsan.supplier.file;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 첨부파일 정보 저장소 */
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /** 삭제하지 않은 파일만 ID로 찾는다. */
    Optional<StoredFile> findByIdAndDeletedAtIsNull(Long id);

    /** 업체 일반 자료와 계약·인증 첨부를 함께 조회한다. 갱신된 건의 파일도 포함하고 삭제된 파일은 제외한다. */
    @EntityGraph(attributePaths = {"contract", "certification", "certification.certType"})
    @Query("""
            select f from StoredFile f
            left join f.contract c left join f.certification cert
            where f.deletedAt is null
              and (f.supplier.id = :supplierId or c.supplier.id = :supplierId or cert.supplier.id = :supplierId)
            order by f.createdAt desc, f.id desc""")
    Page<StoredFile> findForSupplier(@Param("supplierId") Long supplierId, Pageable pageable);

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

    /** 계약 하나에 연결된 첨부(삭제 제외)를 최신 순으로 찾는다. */
    List<StoredFile> findByContractIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long contractId);

    /** 인증 하나에 연결된 첨부(삭제 제외)를 최신 순으로 찾는다. */
    List<StoredFile> findByCertificationIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long certificationId);

    /** 목록 화면에서 첨부 아이콘을 한 번에 표시하기 위해, 여러 계약의 첨부 개수를 한 번에 센다. */
    @Query("""
            select f.contract.id as id, count(f) as count from StoredFile f
            where f.deletedAt is null and f.contract.id in :ids
            group by f.contract.id""")
    List<IdCount> countByContractIds(@Param("ids") List<Long> ids);

    /** 목록 화면에서 첨부 아이콘을 한 번에 표시하기 위해, 여러 인증의 첨부 개수를 한 번에 센다. */
    @Query("""
            select f.certification.id as id, count(f) as count from StoredFile f
            where f.deletedAt is null and f.certification.id in :ids
            group by f.certification.id""")
    List<IdCount> countByCertificationIds(@Param("ids") List<Long> ids);

    /** id별 개수 한 행 (위 두 집계 쿼리의 결과 형식). */
    interface IdCount {
        Long getId();
        long getCount();
    }
}
