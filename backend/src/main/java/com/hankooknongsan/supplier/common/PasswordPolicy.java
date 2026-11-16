package com.hankooknongsan.supplier.common;

import java.nio.charset.StandardCharsets;

/**
 * 비밀번호 규칙 (가입·마이페이지 공통).
 * 길이(8~64자)는 각 요청 DTO의 {@code @Size}로 먼저 걸러지고, 여기서는 bcrypt 제한(UTF-8 72바이트)만 확인한다.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** 비밀번호가 UTF-8 기준 72바이트를 넘으면 해당 필드 오류(400)를 던진다. */
    public static void checkByteLength(String field, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw BusinessException.invalid(field, "비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요.");
        }
    }
}
