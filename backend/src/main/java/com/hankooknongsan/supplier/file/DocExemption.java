package com.hankooknongsan.supplier.file;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 필수 서류의 업체별 예외 (#85). 공통 필수 서류 중 이 업체에는 해당하지 않는 종류를 뜻한다.
 * 행이 있으면 그 업체는 그 서류가 없어도 누락으로 세지 않는다. 되돌릴 때는 행을 지운다.
 * 업체·종류는 ID만 가져 다른 패키지의 조회(JPQL)에서 이름만으로 참조할 수 있게 한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocExemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 예외를 둔 협력업체 ID */
    private Long supplierId;

    /** 해당 없음으로 뺀 자료 종류 ID */
    private Long categoryId;

    /** 뺀 일시 */
    private LocalDateTime createdAt;

    /** 뺀 사람 아이디 */
    private String createdBy;

    /** 한 업체의 한 종류를 해당 없음으로 뺀다. */
    DocExemption(Long supplierId, Long categoryId, String createdBy, LocalDateTime createdAt) {
        this.supplierId = supplierId;
        this.categoryId = categoryId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
