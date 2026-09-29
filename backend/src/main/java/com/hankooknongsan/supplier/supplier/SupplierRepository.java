package com.hankooknongsan.supplier.supplier;

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
     * 검색어는 업체명·업체코드에 부분 일치(대소문자 무시), 거래상태는 일치 조건이다.
     * 검색어가 빈 문자열이거나 상태가 null이면 해당 조건을 적용하지 않는다.
     */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select s from Supplier s
            where (:keyword = '' or lower(s.name) like lower(concat('%', :keyword, '%'))
                                 or lower(s.code) like lower(concat('%', :keyword, '%')))
              and (:status is null or s.status = :status)
            """)
    Page<Supplier> search(@Param("keyword") String keyword, @Param("status") SupplierStatus status,
                          Pageable pageable);

    /** 거래상태별 업체 수를 센다. (대시보드용) */
    long countByStatus(SupplierStatus status);
}
