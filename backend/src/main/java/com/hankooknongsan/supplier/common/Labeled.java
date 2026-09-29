package com.hankooknongsan.supplier.common;

/**
 * 화면에 보여줄 한글 이름을 가진 코드값(enum)이 구현하는 인터페이스.
 */
public interface Labeled {

    /** 화면 표시용 한글 이름을 반환한다. 예: ACTIVE → "거래중" */
    String label();
}
