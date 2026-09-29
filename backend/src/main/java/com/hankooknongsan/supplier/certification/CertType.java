package com.hankooknongsan.supplier.certification;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증 종류 기준정보 (예: HACCP, GAP).
 * 실제 관리 대상 인증 목록은 미팅 확인 후 DB 마이그레이션으로 조정한다.
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

    /** 선택상자 표시 순서 */
    private int sortOrder;
}
