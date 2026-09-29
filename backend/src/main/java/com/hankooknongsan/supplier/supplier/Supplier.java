package com.hankooknongsan.supplier.supplier;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 협력업체 엔티티 (FR-001~004).
 * 업체 기본정보와 내부 담당자를 가진다. 계약·인증은 각 기능에서 이 업체를 참조한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 업체코드. 기존 체계가 확인되기 전까지 SUP-0001 형식으로 자체 채번한다. */
    private String code;
    /** 업체명 */
    private String name;
    /** 사업자번호 (000-00-00000). 저장 허용 여부는 미팅 확인 필요 */
    private String businessNumber;

    /** 거래상태 */
    @Enumerated(EnumType.STRING)
    private SupplierStatus status;

    /** 업체 측 담당자 이름 */
    private String contactName;
    /** 업체 측 담당자 연락처 */
    private String contactPhone;
    /** 업체 측 담당자 이메일 */
    private String contactEmail;

    /** 우리 회사 내부 담당자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** 비고 */
    private String memo;

    /** 업체명과 거래상태로 새 협력업체를 만든다. 나머지 정보는 {@link #update}로 채운다. */
    public Supplier(String name, SupplierStatus status) {
        this.name = name;
        this.status = status;
    }

    /** 협력업체 기본정보를 한 번에 수정한다. */
    public void update(String name, String businessNumber, SupplierStatus status, String contactName,
                       String contactPhone, String contactEmail, AppUser owner, String memo) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.status = status;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.owner = owner;
        this.memo = memo;
    }

    /** 업체코드를 부여한다. 저장 후 발급된 ID로 코드를 만들기 때문에 저장 직후에만 호출한다. */
    public void assignCode(String code) {
        this.code = code;
    }
}
