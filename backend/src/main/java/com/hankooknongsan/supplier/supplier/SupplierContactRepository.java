package com.hankooknongsan.supplier.supplier;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 협력업체 추가 담당자 저장소 */
public interface SupplierContactRepository extends JpaRepository<SupplierContact, Long> {

    /** 한 업체의 담당자를 등록한 순서대로 조회한다. */
    List<SupplierContact> findBySupplierIdOrderByIdAsc(Long supplierId);

    /** 그 업체에 속한 담당자만 찾는다. 다른 업체의 담당자 ID로 고치거나 지우는 것을 막는다. */
    Optional<SupplierContact> findByIdAndSupplierId(Long id, Long supplierId);
}
