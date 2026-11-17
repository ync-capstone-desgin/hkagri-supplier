package com.hankooknongsan.supplier.history;

/** 변경 이력에 남기는 작업 종류 (#45). */
public enum ChangeAction {
    CREATE("등록"),
    UPDATE("수정"),
    RENEW("갱신"),
    STATUS("처리상태 변경"),
    DELETE("삭제"),
    RESTORE("되살리기");

    private final String label;

    /** 작업 종류의 한글 이름을 지정한다. */
    ChangeAction(String label) {
        this.label = label;
    }

    /** 작업 종류의 한글 이름을 반환한다. */
    public String label() {
        return label;
    }
}
