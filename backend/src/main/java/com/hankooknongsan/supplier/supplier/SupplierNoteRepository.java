package com.hankooknongsan.supplier.supplier;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 협력업체 소통 메모 저장소 */
public interface SupplierNoteRepository extends JpaRepository<SupplierNote, Long> {

    /** 한 업체의 메모를 소통한 날짜가 최근인 순(같은 날은 나중에 쓴 것부터)으로 조회한다. */
    Page<SupplierNote> findBySupplierIdOrderByContactDateDescIdDesc(Long supplierId, Pageable pageable);

    /** 그 업체에 속한 메모만 찾는다. */
    Optional<SupplierNote> findByIdAndSupplierId(Long id, Long supplierId);
}
