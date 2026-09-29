package com.hankooknongsan.supplier.certification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 인증 종류 저장소 */
public interface CertTypeRepository extends JpaRepository<CertType, String> {

    /** 표시 순서대로 전체 인증 종류를 조회한다. */
    List<CertType> findAllByOrderBySortOrder();
}
