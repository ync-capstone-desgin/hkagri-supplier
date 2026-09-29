package com.hankooknongsan.supplier.expiry;

import com.hankooknongsan.supplier.common.Labeled;

/**
 * 만료 예정 목록의 조회 구간 (화면의 기간 칩).
 * 7·30·90일 구간은 오늘(D-0)부터 N일 안에 만료되는 항목이며, 이미 만료된 항목은 "만료됨"에서 따로 본다.
 */
public enum ExpiryPeriod implements Labeled {
    EXPIRED("만료됨", null),
    D7("7일 이내", 7),
    D30("30일 이내", 30),
    D90("90일 이내", 90),
    UNKNOWN("확인필요", null);

    private final String label;
    private final Integer days;

    /** 구간의 한글 이름과 일수를 지정한다. */
    ExpiryPeriod(String label, Integer days) {
        this.label = label;
        this.days = days;
    }

    /** 구간의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }

    /** 오늘부터 며칠 안인지 반환한다. 만료됨·확인필요 구간은 null. */
    public Integer days() {
        return days;
    }
}
