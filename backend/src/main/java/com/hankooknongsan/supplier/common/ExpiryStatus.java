package com.hankooknongsan.supplier.common;

/**
 * 날짜로 계산하는 만료상태 (FR-007). DB에 저장하지 않고 조회할 때마다 계산한다.
 * 담당자가 기록하는 갱신 처리상태({@link RenewalStatus})와는 별개다.
 */
public enum ExpiryStatus implements Labeled {
    EXPIRED("만료"),
    IMMINENT("만료예정"),
    NORMAL("정상"),
    UNKNOWN("확인필요");

    private final String label;

    /** 상태의 한글 이름을 지정한다. */
    ExpiryStatus(String label) {
        this.label = label;
    }

    /** 상태의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }
}
