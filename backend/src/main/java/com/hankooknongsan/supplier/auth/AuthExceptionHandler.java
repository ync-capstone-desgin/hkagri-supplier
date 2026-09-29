package com.hankooknongsan.supplier.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 로그인 API 전용 오류 처리기 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    /** 로그인 실패를 401 응답으로 변환한다. 아이디와 비밀번호 중 무엇이 틀렸는지는 알려주지 않는다. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handle(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
