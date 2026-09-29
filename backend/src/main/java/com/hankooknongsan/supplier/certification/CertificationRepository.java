package com.hankooknongsan.supplier.certification;

import com.hankooknongsan.supplier.common.RenewalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 인증 저장소 */
public interface CertificationRepository extends JpaRepository<Certification, Long> {

    /**
     * 인증을 검색한다.
     * 검색어는 인증번호·업체명에 부분 일치, 나머지는 일치 조건이며 빈 값·null이면 무시한다.
     * includeInactive가 false면 갱신되어 비활성이 된 이전 인증은 제외한다.
     */
    @EntityGraph(attributePaths = {"supplier", "owner", "certType"})
    @Query("""
            select x from Certification x
            where (:supplierId is null or x.supplier.id = :supplierId)
              and (:keyword = '' or lower(x.certificateNumber) like lower(concat('%', :keyword, '%'))
                                 or lower(x.supplier.name) like lower(concat('%', :keyword, '%')))
              and (:certType = '' or x.certType.code = :certType)
              and (:ownerId is null or x.owner.id = :ownerId)
              and (:renewalStatus is null or x.renewalStatus = :renewalStatus)
              and (:includeInactive = true or x.active = true)
            """)
    Page<Certification> search(@Param("supplierId") Long supplierId, @Param("keyword") String keyword,
                               @Param("certType") String certType, @Param("ownerId") Long ownerId,
                               @Param("renewalStatus") RenewalStatus renewalStatus,
                               @Param("includeInactive") boolean includeInactive, Pageable pageable);

    /** 같은 인증 종류·인증번호가 이미 등록돼 있는지 찾는다. (중복 경고용, 자기 자신은 제외) */
    @Query("""
            select count(x) > 0 from Certification x
            where x.certType.code = :certType and x.certificateNumber = :number and x.active = true
              and (:exceptId is null or x.id <> :exceptId)
            """)
    boolean existsDuplicate(@Param("certType") String certType, @Param("number") String number,
                            @Param("exceptId") Long exceptId);
}
