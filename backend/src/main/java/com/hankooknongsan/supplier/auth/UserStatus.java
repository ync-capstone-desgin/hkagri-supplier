package com.hankooknongsan.supplier.auth;

/** 계정 이용 상태. 가입 신청은 PENDING이며 관리자 승인 후 ACTIVE가 된다. */
public enum UserStatus {
    PENDING, ACTIVE, REJECTED, INACTIVE
}
