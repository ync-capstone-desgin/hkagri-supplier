package com.hankooknongsan.supplier.history;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 변경 이력 저장소 */
public interface ChangeLogRepository extends JpaRepository<ChangeLog, Long> {

    /** 한 업체와 그 업체의 계약·인증 이력을 최신순으로 조회한다. (업체 상세의 변경 이력 탭) */
    Page<ChangeLog> findBySupplierIdOrderByCreatedAtDescIdDesc(Long supplierId, Pageable pageable);

    /** 같은 대상에 이 기록보다 나중 기록이 있는지. 삭제 뒤에는 되살리기 말고 남는 기록이 없다. (#72) */
    boolean existsByTargetTypeAndTargetIdAndIdGreaterThan(ChangeTarget targetType, Long targetId, Long id);

    /** 대상의 가장 최근 기록. 영구삭제 기록에 업체명·대상 이름을 옮겨 적는 데 쓴다. (#77) */
    Optional<ChangeLog> findFirstByTargetTypeAndTargetIdOrderByIdDesc(ChangeTarget targetType, Long targetId);

    /**
     * 전체 이력을 최신순으로 검색한다. (관리자용)
     * 검색어는 업체명·대상 이름·작업자 아이디·작업자 이름에 부분 일치, 대상 종류는 null이면 전체다.
     */
    @Query("""
            select h from ChangeLog h
            where (:targetType is null or h.targetType = :targetType)
              and (:keyword = '' or lower(h.supplierName) like lower(concat('%', :keyword, '%'))
                                 or lower(h.targetName) like lower(concat('%', :keyword, '%'))
                                 or lower(h.actor) like lower(concat('%', :keyword, '%'))
                                 or lower(h.actorName) like lower(concat('%', :keyword, '%')))
            order by h.createdAt desc, h.id desc
            """)
    Page<ChangeLog> search(@Param("targetType") ChangeTarget targetType, @Param("keyword") String keyword,
                           Pageable pageable);
}
