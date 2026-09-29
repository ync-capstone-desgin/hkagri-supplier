package com.hankooknongsan.supplier.notification;

import com.hankooknongsan.supplier.common.Labeled;

/** 채널별 발송 상태 */
public enum DeliveryStatus implements Labeled {
    PENDING("대기"),
    SENT("발송완료"),
    FAILED("실패");

    private final String label;

    /** 상태의 한글 이름을 지정한다. */
    DeliveryStatus(String label) {
        this.label = label;
    }

    /** 상태의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }
}
