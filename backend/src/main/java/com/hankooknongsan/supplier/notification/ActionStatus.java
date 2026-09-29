package com.hankooknongsan.supplier.notification;

import com.hankooknongsan.supplier.common.Labeled;

/** 알림을 받은 사람의 업무 처리상태. 발송 성공 여부({@link DeliveryStatus})와는 별개다. */
public enum ActionStatus implements Labeled {
    UNREAD("미확인"),
    READ("확인"),
    IN_PROGRESS("처리중"),
    DONE("완료");

    private final String label;

    /** 상태의 한글 이름을 지정한다. */
    ActionStatus(String label) {
        this.label = label;
    }

    /** 상태의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }
}
