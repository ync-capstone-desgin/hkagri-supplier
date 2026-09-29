package com.hankooknongsan.supplier.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API 공통 오류 처리기.
 * 모든 오류 응답을 RFC 7807 ProblemDetail 형식으로 통일하고,
 * 입력 오류는 {@code errors: [{field, message}]} 목록을 붙여 화면이 칸별로 안내할 수 있게 한다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 입력칸 하나의 오류 내용 */
    public record FieldError(String field, String message) {
    }

    /** @Valid 검증 실패(필수값 누락, 형식 오류)를 400 응답으로 변환한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요.");
        pd.setProperty("errors", errors);
        return pd;
    }

    /** 업무 규칙 위반을 해당 HTTP 상태의 응답으로 변환한다. */
    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        if (e.getField() != null) {
            pd.setProperty("errors", List.of(new FieldError(e.getField(), e.getMessage())));
        }
        return pd;
    }

    /** 권한 부족을 403 응답으로 변환한다. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
}
