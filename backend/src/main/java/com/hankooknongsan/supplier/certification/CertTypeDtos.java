package com.hankooknongsan.supplier.certification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 인증 종류 관리 요청. 코드는 서버가 생성하며 변경하지 않는다. */
public final class CertTypeDtos {
    /** 요청 형식만 제공하는 클래스의 생성을 막는다. */
    private CertTypeDtos() {
    }

    /** 새 종류의 표시 이름을 받는다. */
    public record CreateRequest(
            @NotBlank(message = "인증 종류 이름을 입력해 주세요.")
            @Size(max = 100, message = "이름은 100자 이내로 입력해 주세요.") String name) {
    }

    /** 사용 여부를 덮어쓰지 않고 이름만 변경한다. */
    public record UpdateRequest(
            @NotBlank(message = "인증 종류 이름을 입력해 주세요.")
            @Size(max = 100, message = "이름은 100자 이내로 입력해 주세요.") String name) {
    }

    /** 이름을 덮어쓰지 않고 사용 여부만 변경한다. */
    public record ActiveRequest(
            @NotNull(message = "사용 여부를 선택해 주세요.") Boolean active) {
    }
}
