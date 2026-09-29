package com.hankooknongsan.supplier.notification;

import com.hankooknongsan.supplier.common.Labeled;

/** 알림 발송 채널. 문자·메신저는 PoC 범위 밖이며, 추가하려면 채널 구현체만 늘리면 된다. */
public enum Channel implements Labeled {
    IN_APP("화면"),
    EMAIL("이메일");

    private final String label;

    /** 채널의 한글 이름을 지정한다. */
    Channel(String label) {
        this.label = label;
    }

    /** 채널의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }
}
