package com.hankooknongsan.supplier.contract;

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
 * 협력업체 계약 엔티티 (FR-005).
 * <p>
 * 갱신할 때는 기존 행을 고치지 않고 새 계약을 등록한 뒤 {@code prev}로 이전 계약을 연결한다.
 * 이전 계약은 {@code active=false}가 되어 목록에서 빠지지만 이력으로 남는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 계약 상대 협력업체 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** 계약번호 (번호체계는 미팅 확인 필요) */
    private String contractNumber;

    /** 계약명 */
    private String title;

    /** 계약 시작일 */
    private LocalDate startDate;

    /** 계약 종료일. null이면 무기한 계약 */
    private LocalDate endDate;

    /** 자동연장 조항 여부 */
    private boolean autoRenew;

    /** 담당자가 기록하는 갱신 처리상태 */
    @Enumerated(EnumType.STRING)
    private RenewalStatus renewalStatus;

    /** 우리 회사 내부 담당자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** 갱신 전 계약 (갱신으로 등록된 경우) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_id")
    private Contract prev;

    /** 현재 유효한 계약인지. 갱신되면 false */
    private boolean active;

    /** 계약서 파일 위치(공유폴더·문서함 링크) */
    private String documentUrl;

    /** 비고 */
    private String memo;

    /** 새 계약을 만든다. 갱신 처리상태는 "미착수", 유효 상태로 시작한다. */
    public Contract(Supplier supplier) {
        this.supplier = supplier;
        this.renewalStatus = RenewalStatus.NONE;
        this.active = true;
    }

    /** 계약 정보를 한 번에 수정한다. */
    public void update(Supplier supplier, String contractNumber, String title, LocalDate startDate, LocalDate endDate,
                       boolean autoRenew, AppUser owner, String documentUrl, String memo) {
        this.supplier = supplier;
        this.contractNumber = contractNumber;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.autoRenew = autoRenew;
        this.owner = owner;
        this.documentUrl = documentUrl;
        this.memo = memo;
    }

    /** 갱신 처리상태를 바꾼다. */
    public void changeRenewalStatus(RenewalStatus status) {
        this.renewalStatus = status;
    }

    /**
     * 이 계약이 새 계약으로 갱신되었음을 기록한다.
     * 처리상태는 "갱신완료", 유효 여부는 false가 된다.
     */
    public void markRenewed() {
        this.renewalStatus = RenewalStatus.DONE;
        this.active = false;
    }

    /** 갱신으로 등록된 새 계약에 이전 계약을 연결한다. */
    public void linkPrevious(Contract prev) {
        this.prev = prev;
    }
}
