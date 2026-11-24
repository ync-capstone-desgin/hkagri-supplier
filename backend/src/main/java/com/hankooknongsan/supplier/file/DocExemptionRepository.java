package com.hankooknongsan.supplier.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 필수 서류 업체별 예외 저장소 (#85) */
public interface DocExemptionRepository extends JpaRepository<DocExemption, Long> {

    /** 이미 해당 없음으로 뺀 종류인지 확인한다. */
    boolean existsBySupplierIdAndCategoryId(Long supplierId, Long categoryId);

    /** 예외를 되돌린다. 지운 건수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DocExemption e where e.supplierId = :supplierId and e.categoryId = :categoryId")
    int deleteOne(@Param("supplierId") Long supplierId, @Param("categoryId") Long categoryId);

    /** 협력업체를 영구삭제할 때 그 업체의 예외를 모두 지운다. (#77) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DocExemption e where e.supplierId = :supplierId")
    int deleteBySupplier(@Param("supplierId") Long supplierId);
}
