package com.hankooknongsan.supplier.certification;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증 종류 기준정보 (예: HACCP, GAP).
 * 코드는 유지하고 관리자가 표시 이름·사용 여부를 관리한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertType {

    /** 인증 종류 코드 */
    @Id
    private String code;

    /** 화면 표시 이름 */
    private String name;

    /** 공백·대소문자를 제외한 이름의 중복 비교 키 */
    @Column(nullable = false, length = 300)
    private String nameKey;

    /** 새 인증에서 선택할 수 있는지 여부. 기존 인증 연결은 보존한다. */
    private boolean active = true;

    /** 선택상자 표시 순서 */
    private int sortOrder;

    /** 새 종류는 사용 상태로 시작한다. */
    public CertType(String code, String name, String nameKey, int sortOrder) {
        this.code = code;
        this.name = name;
        this.nameKey = nameKey;
        this.sortOrder = sortOrder;
    }

    /** 식별자와 표시 순서를 유지하면서 관리 항목만 바꾼다. */
    public void update(String name, String nameKey, boolean active) {
        this.name = name;
        this.nameKey = nameKey;
        this.active = active;
    }
}
