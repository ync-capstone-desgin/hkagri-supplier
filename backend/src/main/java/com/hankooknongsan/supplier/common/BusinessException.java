package com.hankooknongsan.supplier.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반을 알리는 예외.
 * HTTP 상태와, 특정 입력칸의 오류라면 해당 필드 이름을 함께 담아 화면이 입력칸 아래에 바로 표시할 수 있게 한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String field;

    /** 특정 필드와 관계없는 업무 오류를 만든다. */
    public BusinessException(HttpStatus status, String message) {
        this(status, null, message);
    }

    /** 특정 입력 필드에 대한 업무 오류를 만든다. */
    public BusinessException(HttpStatus status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    /** 대상을 찾지 못했을 때(404) 쓰는 예외를 만든다. 예: notFound("협력업체") */
    public static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, what + "을(를) 찾을 수 없습니다.");
    }

    /** 입력값이 업무 규칙에 맞지 않을 때(400) 쓰는 예외를 만든다. */
    public static BusinessException invalid(String field, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, field, message);
    }
}
