package com.hankooknongsan.supplier.auth;

import com.hankooknongsan.supplier.common.Labeled;

/**
 * 사용자 역할.
 * ADMIN: 모든 기능 + 기준정보·시연 도구, MANAGER: 업체·계약·인증 등록·수정, VIEWER: 조회만 가능.
 */
public enum Role implements Labeled {
    ADMIN("관리자"),
    MANAGER("담당자"),
    VIEWER("조회자");

    private final String label;

    /** 역할의 한글 이름을 지정한다. */
    Role(String label) {
        this.label = label;
    }

    /** 역할의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }
}
