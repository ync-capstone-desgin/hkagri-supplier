package com.hankooknongsan.supplier.common;

/** 만료 관리 대상의 종류 */
public enum TargetType implements Labeled {
    CONTRACT("계약"),
    CERTIFICATION("인증");

    private final String label;

    /** 대상 종류의 한글 이름을 지정한다. */
    TargetType(String label) {
        this.label = label;
    }

    /** 대상 종류의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }
}
