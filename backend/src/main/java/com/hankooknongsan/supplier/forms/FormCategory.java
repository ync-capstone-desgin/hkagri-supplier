package com.hankooknongsan.supplier.forms;

import com.hankooknongsan.supplier.common.BusinessException;

/** 공용 양식 분류. 화면에서는 계약 양식·점검표·기타로 표시한다. */
public enum FormCategory {
    CONTRACT, CHECKLIST, OTHER;

    /** 알 수 없는 분류를 서버 오류 대신 category 필드 오류로 안내한다. */
    static FormCategory parse(String value) {
        if (value == null) throw BusinessException.invalid("category", "분류를 선택해 주세요.");
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalid("category", "올바른 분류를 선택해 주세요.");
        }
    }
}
