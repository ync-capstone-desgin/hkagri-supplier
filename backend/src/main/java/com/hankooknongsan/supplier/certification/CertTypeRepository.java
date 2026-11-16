package com.hankooknongsan.supplier.certification;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 인증 종류 저장소 */
public interface CertTypeRepository extends JpaRepository<CertType, String> {

    /** 표시 순서대로 전체 인증 종류를 조회한다. */
    @Query("select t from CertType t order by t.sortOrder, t.code")
    List<CertType> findAllByOrderBySortOrder();

    /** 비활성 종류도 중복 검사의 대상이다. */
    boolean existsByNameKeyAndCodeNot(String nameKey, String code);

    /** 기존 표시 순서를 바꾸지 않고 마지막 위치를 구한다. */
    @Query("select coalesce(max(t.sortOrder), 0) + 1 from CertType t")
    int nextSortOrder();

    /** 인증 저장이 끝날 때까지 관리자의 비활성화와 순서를 보장한다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from CertType t where t.code = :code")
    Optional<CertType> findForSelection(String code);

    /** 종류 변경끼리 직렬화하고 진행 중인 인증 저장을 기다린다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CertType t where t.code = :code")
    Optional<CertType> findForUpdate(String code);
}
