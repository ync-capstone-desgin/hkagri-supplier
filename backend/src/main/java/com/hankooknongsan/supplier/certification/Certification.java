package com.hankooknongsan.supplier.certification;

import java.time.LocalDate;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.common.BaseEntity;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.supplier.Supplier;
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
 * 협력업체 인증 엔티티 (FR-006).
 * <p>
 * 계약과 같은 방식으로, 갱신 시 새 인증을 등록하고 {@code prev}로 이전 인증을 연결한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 인증을 보유한 협력업체 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** 인증 종류 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cert_type_code")
    private CertType certType;

    /** 인증번호 */
    private String certificateNumber;

    /** 발급일 */
    private LocalDate issuedDate;

    /** 만료일. null이면 만료일 없음(확인 필요) */
    private LocalDate expiryDate;

    /** 담당자가 기록하는 갱신 처리상태 */
    @Enumerated(EnumType.STRING)
    private RenewalStatus renewalStatus;

    /** 우리 회사 내부 담당자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** 갱신 전 인증 (갱신으로 등록된 경우) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_id")
    private Certification prev;

    /** 현재 유효한 인증인지. 갱신되면 false */
    private boolean active;

    /** 인증서 파일 위치(공유폴더·문서함 링크) */
    private String documentUrl;

    /** 비고 */
    private String memo;

    /** 새 인증을 만든다. 갱신 처리상태는 "미착수", 유효 상태로 시작한다. */
    public Certification(Supplier supplier) {
        this.supplier = supplier;
        this.renewalStatus = RenewalStatus.NONE;
        this.active = true;
    }

    /** 인증 정보를 한 번에 수정한다. */
    public void update(Supplier supplier, CertType certType, String certificateNumber, LocalDate issuedDate,
                       LocalDate expiryDate, AppUser owner, String documentUrl, String memo) {
        this.supplier = supplier;
        this.certType = certType;
        this.certificateNumber = certificateNumber;
        this.issuedDate = issuedDate;
        this.expiryDate = expiryDate;
        this.owner = owner;
        this.documentUrl = documentUrl;
        this.memo = memo;
    }

    /** 갱신 처리상태를 바꾼다. */
    public void changeRenewalStatus(RenewalStatus status) {
        this.renewalStatus = status;
    }

    /** 이 인증이 새 인증으로 갱신되었음을 기록한다. 처리상태는 "갱신완료", 유효 여부는 false가 된다. */
    public void markRenewed() {
        this.renewalStatus = RenewalStatus.DONE;
        this.active = false;
    }

    /** 갱신으로 등록된 새 인증에 이전 인증을 연결한다. */
    public void linkPrevious(Certification prev) {
        this.prev = prev;
    }
}
