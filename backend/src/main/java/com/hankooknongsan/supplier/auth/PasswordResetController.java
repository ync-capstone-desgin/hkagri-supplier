package com.hankooknongsan.supplier.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 찾기·재설정 API (FR-029).
 * 로그인하지 않은 사용자도 호출할 수 있다({@link SecurityConfig}).
 */
@RestController
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService service;

    /** 아이디 또는 이메일 */
    public record ForgotRequest(@NotBlank(message = "아이디 또는 이메일을 입력해 주세요.") String usernameOrEmail) {
    }

    /** 메일 속 토큰과 새 비밀번호 */
    public record ResetRequest(@NotBlank(message = "유효하지 않은 링크입니다.") String token,
                               @NotBlank(message = "새 비밀번호를 입력해 주세요.")
                               @Size(min = 8, max = 64, message = "새 비밀번호는 8~64자로 입력해 주세요.") String newPassword) {
    }

    /** 계정 존재 여부를 드러내지 않도록 결과와 상관없이 같은 문구를 돌려준다. */
    public record ForgotResponse(String message) {
    }

    /** 해당 계정이 있으면 재설정 메일을 보낸다. 없어도 같은 응답을 돌려준다. */
    @PostMapping("/api/auth/forgot-password")
    public ForgotResponse forgot(@Valid @RequestBody ForgotRequest req) {
        service.request(req.usernameOrEmail().trim());
        return new ForgotResponse("해당 계정이 있으면 비밀번호 재설정 메일을 보냈습니다.");
    }

    /** 유효한 토큰이면 비밀번호를 바꾼다. 만료·사용된 토큰은 400으로 거절한다. */
    @PostMapping("/api/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest req) {
        service.reset(req.token(), req.newPassword());
    }
}
