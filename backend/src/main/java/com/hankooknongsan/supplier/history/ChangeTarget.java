package com.hankooknongsan.supplier.history;

/** 변경 이력을 남기는 대상 종류 (#45). */
public enum ChangeTarget {
    SUPPLIER("협력업체"),
    CONTRACT("계약"),
    CERTIFICATION("인증");

    private final String label;

    /** 대상 종류의 한글 이름을 지정한다. */
    ChangeTarget(String label) {
        this.label = label;
    }

    /** 대상 종류의 한글 이름을 반환한다. */
    public String label() {
        return label;
    }
}
