package com.hankooknongsan.supplier.supplier;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 협력업체 저장소 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * 협력업체를 검색한다 (FR-002).
     * 검색어는 업체명·업체코드에 부분 일치(대소문자 무시), 거래상태는 일치 조건,
     * 인증 종류는 해당 인증을 유효하게 보유한 업체만 남긴다.
     * 빈 문자열이거나 null인 조건은 적용하지 않는다.
     * certExpired가 true면 필수 인증(#46)의 현재 인증이 오늘 기준으로 만료된 업체만 남긴다.
     * missingDocs가 true면 필수 서류(#47)가 하나라도 빠진 업체만 남긴다. 자료 종류·파일은 JPQL 엔티티 이름으로만 참조한다.
     */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select s from Supplier s
            where (:keyword = '' or lower(s.name) like lower(concat('%', :keyword, '%'))
                                 or lower(s.code) like lower(concat('%', :keyword, '%')))
              and (:status is null or s.status = :status)
              and (:certType = '' or exists (select 1 from Certification x
                                             where x.supplier = s and x.active = true and x.certType.code = :certType))
              and (:certExpired = false or exists (select 1 from Certification x where x.supplier = s and x.active = true and x.certType.required = true and x.expiryDate < :today))
              and (:missingDocs = false or exists (
                     select 1 from FileCategory fc
                     where fc.required = true and fc.active = true and not exists (
                         select 1 from StoredFile f left join f.contract fct left join f.certification fx
                         where f.category = fc and f.deletedAt is null
                           and (f.supplier = s or fct.supplier = s or fx.supplier = s))))
            """)
    Page<Supplier> search(@Param("keyword") String keyword, @Param("status") SupplierStatus status,
                          @Param("certType") String certType, @Param("missingDocs") boolean missingDocs,
                          @Param("certExpired") boolean certExpired, @Param("today") LocalDate today,
                          Pageable pageable);

    /** 업체별 유효 계약·인증 건수와, 기준일(horizon) 이전에 만료되는(이미 만료 포함) 건수 */
    interface SupplierCounts {

        /** 업체 ID */
        Long getId();

        /** 유효 계약 수 */
        long getContractCount();

        /** 유효 인증 수 */
        long getCertCount();

        /** 만료됐거나 기준일까지 만료되는 유효 계약 수 */
        long getExpiringContractCount();

        /** 만료됐거나 기준일까지 만료되는 유효 인증 수 */
        long getExpiringCertCount();

        /** 만료된 필수 인증 수 (#46). 0보다 크면 "인증 만료"로 표시한다. */
        long getExpiredRequiredCertCount();

        /** 빠진 필수 서류 수 (#47). 필수 종류가 없으면 0 */
        long getMissingDocCount();

        /** 만료·임박 건수 합계 (계약 + 인증) */
        default long getExpiringCount() {
            return getExpiringContractCount() + getExpiringCertCount();
        }
    }

    /**
     * 여러 업체의 계약·인증 건수를 한 번에 센다. (목록 그리드의 계약수·인증수·만료건수 컬럼)
     * 계약·인증은 JPQL 엔티티 이름으로만 참조해 supplier → contract 패키지 의존이 생기지 않게 한다.
     */
    @Query("""
            select s.id as id,
                   (select count(c) from Contract c where c.supplier = s and c.active = true) as contractCount,
                   (select count(x) from Certification x where x.supplier = s and x.active = true) as certCount,
                   (select count(c) from Contract c
                     where c.supplier = s and c.active = true and c.endDate <= :horizon) as expiringContractCount,
                   (select count(x) from Certification x
                     where x.supplier = s and x.active = true and x.expiryDate <= :horizon) as expiringCertCount,
                   (select count(x) from Certification x where x.supplier = s and x.active = true and x.certType.required = true and x.expiryDate < :today) as expiredRequiredCertCount,
                   (select count(fc) from FileCategory fc
                     where fc.required = true and fc.active = true and not exists (
                         select 1 from StoredFile f left join f.contract fct left join f.certification fx
                         where f.category = fc and f.deletedAt is null
                           and (f.supplier = s or fct.supplier = s or fx.supplier = s))) as missingDocCount
            from Supplier s where s.id in :ids
            """)
    List<SupplierCounts> counts(@Param("ids") Collection<Long> ids, @Param("horizon") LocalDate horizon,
                                @Param("today") LocalDate today);

    /** 필수 인증이 만료된 업체 수를 센다. (대시보드용, #46) */
    @Query("select count(s) from Supplier s where exists (select 1 from Certification x where x.supplier = s and x.active = true and x.certType.required = true and x.expiryDate < :today)")
    long countCertExpired(@Param("today") LocalDate today);

    /** 업체에 남아 있는(삭제되지 않은) 계약 수. 이전 이력도 센다. (업체 삭제 가능 여부 확인용) */
    @Query("select count(c) from Contract c where c.supplier.id = :id")
    long countContracts(@Param("id") Long id);

    /** 업체에 남아 있는(삭제되지 않은) 인증 수. 이전 이력도 센다. (업체 삭제 가능 여부 확인용) */
    @Query("select count(x) from Certification x where x.supplier.id = :id")
    long countCertifications(@Param("id") Long id);

    /** 거래상태별 업체 수를 센다. (대시보드용) */
    long countByStatus(SupplierStatus status);

    /** 삭제된 협력업체인지 확인한다. 삭제된 행은 일반 조회에서 빠지므로 표를 직접 센다. (#72 되살리기) */
    @Query(value = "select count(*) from supplier where id = :id and deleted_at is not null", nativeQuery = true)
    long countDeleted(@Param("id") Long id);

    /** 지운 표시를 지운다. 되살린 건수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update supplier set deleted_at = null, deleted_by = null where id = :id and deleted_at is not null", nativeQuery = true)
    int undelete(@Param("id") Long id);
}
