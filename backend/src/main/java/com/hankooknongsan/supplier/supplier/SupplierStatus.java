package com.hankooknongsan.supplier.supplier;

import com.hankooknongsan.supplier.common.Labeled;

/** 협력업체 거래상태. 실제 상태 종류는 미팅 확인 필요. */
public enum SupplierStatus implements Labeled {
    ACTIVE("거래중"),
    SUSPENDED("거래중지"),
    TERMINATED("거래종료");

    private final String label;

    /** 상태의 한글 이름을 지정한다. */
    SupplierStatus(String label) {
        this.label = label;
    }

    /** 상태의 한글 이름을 반환한다. */
    @Override
    public String label() {
        return label;
    }
}
