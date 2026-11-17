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

    /** 업체 일반 자료와 계약·인증 첨부를 함께 조회한다. 갱신된 건의 파일도 포함하고 삭제된 파일은 제외한다. 종류가 null이면 전체다. */
    @EntityGraph(attributePaths = {"contract", "certification", "certification.certType", "category"})
    @Query("""
            select f from StoredFile f
            left join f.contract c left join f.certification cert
            where f.deletedAt is null
              and (f.supplier.id = :supplierId or c.supplier.id = :supplierId or cert.supplier.id = :supplierId)
              and (:categoryId is null or f.category.id = :categoryId)
            order by f.createdAt desc, f.id desc""")
    Page<StoredFile> findForSupplier(@Param("supplierId") Long supplierId, @Param("categoryId") Long categoryId,
                                     Pageable pageable);

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

    /** 업체에 직접 올린 일반 자료(삭제 제외)를 찾는다. (업체 삭제 시 함께 삭제용) */
    List<StoredFile> findBySupplierIdAndDeletedAtIsNull(Long supplierId);

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

    /** 전체 자료 검색 결과 한 행: 파일과, 양식 자료실 파일이면 그 게시글의 ID·제목·폴더. */
    interface FileHit {
        StoredFile getFile();
        Long getFormId();
        String getFormTitle();
        Long getFolderId();
    }

    /**
     * 업체 자료·계약·인증 첨부·양식 자료실 파일을 한 번에 검색한다 (#53). 최신순.
     * 검색어는 파일 이름, 업체명, 계약명·계약번호, 인증 종류·적용 대상·인증번호, 양식 제목에 부분 일치한다.
     * 삭제된 파일과, 어디에도 연결되지 않은 파일(올리다 만 파일)은 제외한다. 종류가 null이면 전체다.
     * 양식 게시글은 JPQL 엔티티 이름으로만 참조해 file → forms 패키지 의존이 생기지 않게 한다.
     */
    @Query(value = """
            select f as file, p.id as formId, p.title as formTitle, p.folderId as folderId
            from StoredFile f
            left join f.supplier s
            left join f.contract c left join c.supplier cs
            left join f.certification x left join x.supplier xs left join x.certType t
            left join FormPost p on f member of p.files and p.deletedAt is null
            where f.deletedAt is null
              and (s is not null or c is not null or x is not null or p is not null)
              and (:categoryId is null or f.category.id = :categoryId)
              and (:keyword = '' or lower(f.originalName) like lower(concat('%', :keyword, '%'))
                                 or lower(s.name) like lower(concat('%', :keyword, '%'))
                                 or lower(cs.name) like lower(concat('%', :keyword, '%'))
                                 or lower(xs.name) like lower(concat('%', :keyword, '%'))
                                 or lower(c.title) like lower(concat('%', :keyword, '%'))
                                 or lower(c.contractNumber) like lower(concat('%', :keyword, '%'))
                                 or lower(t.name) like lower(concat('%', :keyword, '%'))
                                 or lower(x.scope) like lower(concat('%', :keyword, '%'))
                                 or lower(x.certificateNumber) like lower(concat('%', :keyword, '%'))
                                 or lower(p.title) like lower(concat('%', :keyword, '%')))
            order by f.createdAt desc, f.id desc""",
            countQuery = """
            select count(f)
            from StoredFile f
            left join f.supplier s
            left join f.contract c left join c.supplier cs
            left join f.certification x left join x.supplier xs left join x.certType t
            left join FormPost p on f member of p.files and p.deletedAt is null
            where f.deletedAt is null
              and (s is not null or c is not null or x is not null or p is not null)
              and (:categoryId is null or f.category.id = :categoryId)
              and (:keyword = '' or lower(f.originalName) like lower(concat('%', :keyword, '%'))
                                 or lower(s.name) like lower(concat('%', :keyword, '%'))
                                 or lower(cs.name) like lower(concat('%', :keyword, '%'))
                                 or lower(xs.name) like lower(concat('%', :keyword, '%'))
                                 or lower(c.title) like lower(concat('%', :keyword, '%'))
                                 or lower(c.contractNumber) like lower(concat('%', :keyword, '%'))
                                 or lower(t.name) like lower(concat('%', :keyword, '%'))
                                 or lower(x.scope) like lower(concat('%', :keyword, '%'))
                                 or lower(x.certificateNumber) like lower(concat('%', :keyword, '%'))
                                 or lower(p.title) like lower(concat('%', :keyword, '%')))""")
    Page<FileHit> search(@Param("keyword") String keyword, @Param("categoryId") Long categoryId, Pageable pageable);

    /** id별 개수 한 행 (위 두 집계 쿼리의 결과 형식). */
    interface IdCount {
        Long getId();
        long getCount();
    }

    /**
     * 계약을 삭제할 때 함께 지워진 첨부를 되살린다 (#72). 계약보다 먼저 따로 지운 파일은 그대로 둔다.
     * 계약의 지운 시각과 비교하므로 계약의 지운 표시를 지우기 전에 실행해야 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update stored_file f set deleted_at = null, deleted_by = null
              from contract t where f.contract_id = t.id and t.id = :id and f.deleted_at >= t.deleted_at""", nativeQuery = true)
    int undeleteWithContract(@Param("id") Long id);

    /** 인증을 삭제할 때 함께 지워진 첨부를 되살린다 (#72). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update stored_file f set deleted_at = null, deleted_by = null
              from certification t where f.certification_id = t.id and t.id = :id and f.deleted_at >= t.deleted_at""", nativeQuery = true)
    int undeleteWithCertification(@Param("id") Long id);

    /** 협력업체를 삭제할 때 함께 지워진 일반 자료를 되살린다 (#72). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update stored_file f set deleted_at = null, deleted_by = null
              from supplier t where f.supplier_id = t.id and t.id = :id and f.deleted_at >= t.deleted_at""", nativeQuery = true)
    int undeleteWithSupplier(@Param("id") Long id);

    /**
     * 계약·인증·협력업체 중 하나에 딸린 모든 파일(삭제된 것 포함)의 저장 키를 찾는다 (#77 영구삭제).
     * 셋 중 하나만 주고 나머지는 null로 준다.
     */
    @Query(value = """
            select storage_key from stored_file
             where contract_id = cast(:contractId as bigint)
                or certification_id = cast(:certificationId as bigint)
                or supplier_id = cast(:supplierId as bigint)""", nativeQuery = true)
    List<String> findStorageKeysOf(@Param("contractId") Long contractId, @Param("certificationId") Long certificationId,
                                   @Param("supplierId") Long supplierId);

    /** 계약·인증·협력업체 중 하나에 딸린 모든 파일 기록을 완전히 지운다 (#77). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from stored_file
             where contract_id = cast(:contractId as bigint)
                or certification_id = cast(:certificationId as bigint)
                or supplier_id = cast(:supplierId as bigint)""", nativeQuery = true)
    int purgeOf(@Param("contractId") Long contractId, @Param("certificationId") Long certificationId,
                @Param("supplierId") Long supplierId);
}
