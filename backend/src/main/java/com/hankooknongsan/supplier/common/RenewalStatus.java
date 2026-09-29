package com.hankooknongsan.supplier.common;

/** 담당자가 기록하는 갱신 처리상태. 날짜로 계산되는 만료상태(ExpiryStatus)와 분리한다. */
public enum RenewalStatus implements Labeled {
    NONE("미착수"),
    IN_PROGRESS("갱신진행"),
    DONE("갱신완료"),
    NOT_RENEWING("갱신안함");

    private final String label;

    /** 처리상태의 한글 이름을 지정한다. */
    RenewalStatus(String label) {
        this.label = label;
    }

    /** 처리상태의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }

    /** 더 이상 알림을 보낼 필요가 없는 상태(갱신완료·갱신안함)인지 여부 */
    public boolean closed() {
        return this == DONE || this == NOT_RENEWING;
    }
}
