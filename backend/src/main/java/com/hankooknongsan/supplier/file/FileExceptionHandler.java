package com.hankooknongsan.supplier.file;

import java.util.List;

import com.hankooknongsan.supplier.common.ApiExceptionHandler.FieldError;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 첨부파일 업로드 오류 처리기.
 * 업로드 한도를 넘는 요청은 컨트롤러에 닿기 전에 서버가 거절하므로, 그 오류를 다른 입력 오류와 같은 형식으로 바꿔 준다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class FileExceptionHandler {

    private final FilePolicy policy;

    /** 업로드 한도 초과를 413 응답으로 변환하고, "file" 칸 아래에 한도를 안내한다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        String message = policy.maxSizeMessage();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, message);
        pd.setProperty("errors", List.of(new FieldError("file", message)));
        return pd;
    }
}
