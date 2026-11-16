package com.hankooknongsan.supplier.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인하지 않은 직원의 회원가입 신청 API. 신청만으로 로그인되지 않는다. */
@RestController
@RequiredArgsConstructor
public class SignupController {
    private final SignupService signupService;

    /** 가입 입력값. 역할과 승인 상태는 서버가 정한다. */
    public record SignupRequest(
            @NotBlank(message = "이름을 입력해 주세요.")
            @Size(max = 50, message = "이름은 50자 이내로 입력해 주세요.") String name,
            @NotBlank(message = "아이디를 입력해 주세요.")
            @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = "아이디는 영문·숫자·밑줄 3~50자로 입력해 주세요.") String username,
            @NotBlank(message = "메일을 입력해 주세요.")
            @Email(message = "올바른 메일 주소를 입력해 주세요.")
            @Size(max = 100, message = "메일은 100자 이내로 입력해 주세요.") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.") String password,
            @NotBlank(message = "비밀번호 확인을 입력해 주세요.") String passwordConfirm,
            @Size(max = 1000, message = "가입 사유는 1000자 이내로 입력해 주세요.") String signupReason) {
    }

    /** 유효한 신청을 승인 대기로 저장하고 비밀번호 등 사용자 정보는 반환하지 않는다. */
    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Valid @RequestBody SignupRequest request) {
        signupService.signup(request);
    }
}
