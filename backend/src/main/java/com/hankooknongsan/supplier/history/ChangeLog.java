package com.hankooknongsan.supplier.history;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 변경 이력 한 건 (#45): 누가 언제 어떤 협력업체·계약·인증에 무엇을 했는지.
 * 한 번 남기면 고치지 않는다. 대상이 나중에 삭제되거나 이름이 바뀌어도 읽을 수 있도록
 * 업체명·대상 이름·작업자 이름을 기록 시점의 값으로 함께 저장한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 종류 */
    @Enumerated(EnumType.STRING)
    private ChangeTarget targetType;

    /** 대상(협력업체·계약·인증)의 ID */
    private Long targetId;

    /** 대상이 속한 협력업체 ID. 업체 상세에서 계약·인증 이력까지 모아 보는 데 쓴다. */
    private Long supplierId;

    /** 기록 시점의 업체명 */
    private String supplierName;

    /** 기록 시점의 대상 이름 (계약명, 인증 종류·적용 대상 등) */
    private String targetName;

    /** 작업 종류 */
    @Enumerated(EnumType.STRING)
    private ChangeAction action;

    /** 바뀐 항목 목록(JSON 배열). 항목 이름·이전 값·바뀐 값을 담는다. */
    @Column(columnDefinition = "text")
    private String changes;

    /** 작업한 사람의 아이디 */
    private String actor;

    /** 기록 시점의 작업자 이름 */
    private String actorName;

    /** 작업 일시 */
    private LocalDateTime createdAt;

    /** 변경 이력 한 건을 만든다. */
    ChangeLog(ChangeTarget targetType, Long targetId, Long supplierId, String supplierName, String targetName,
              ChangeAction action, String changes, String actor, String actorName, LocalDateTime createdAt) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.targetName = targetName;
        this.action = action;
        this.changes = changes;
        this.actor = actor;
        this.actorName = actorName;
        this.createdAt = createdAt;
    }
}
