package com.hankooknongsan.supplier.supplier;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 협력업체 저장소 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * 협력업체를 검색한다 (FR-002).
     * 검색어는 업체명·업체코드에 부분 일치(대소문자 무시), 거래상태는 일치 조건,
     * 인증 종류는 해당 인증을 유효하게 보유한 업체만 남긴다.
     * 빈 문자열이거나 null인 조건은 적용하지 않는다.
     */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select s from Supplier s
            where (:keyword = '' or lower(s.name) like lower(concat('%', :keyword, '%'))
                                 or lower(s.code) like lower(concat('%', :keyword, '%')))
              and (:status is null or s.status = :status)
              and (:certType = '' or exists (select 1 from Certification x
                                             where x.supplier = s and x.active = true and x.certType.code = :certType))
            """)
    Page<Supplier> search(@Param("keyword") String keyword, @Param("status") SupplierStatus status,
                          @Param("certType") String certType, Pageable pageable);

    /** 같은 사업자번호로 등록된 업체를 등록순으로 찾는다. (중복 경고용) */
    List<Supplier> findByBusinessNumberOrderByIdAsc(String businessNumber);

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
                     where x.supplier = s and x.active = true and x.expiryDate <= :horizon) as expiringCertCount
            from Supplier s where s.id in :ids
            """)
    List<SupplierCounts> counts(@Param("ids") Collection<Long> ids, @Param("horizon") LocalDate horizon);

    /** 거래상태별 업체 수를 센다. (대시보드용) */
    long countByStatus(SupplierStatus status);
}
