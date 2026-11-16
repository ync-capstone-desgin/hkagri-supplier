package com.hankooknongsan.supplier.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.hankooknongsan.supplier.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 가입 입력값과 중복을 검증하고 암호화한 비밀번호만 저장한다. */
@Service
@RequiredArgsConstructor
public class SignupService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    /** 가입을 처리한다. 저장소 트랜잭션이 끝난 뒤 중복 충돌을 입력칸 오류로 변환한다. */
    public void signup(SignupController.SignupRequest req) {
        if (!req.password().equals(req.passwordConfirm())) {
            throw BusinessException.invalid("passwordConfirm", "비밀번호가 일치하지 않습니다.");
        }
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw BusinessException.invalid("password", "비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요.");
        }
        String email = req.email().toLowerCase(Locale.ROOT);
        checkDuplicates(req.username(), email);
        AppUser user = AppUser.pending(req.username(), encoder.encode(req.password()),
                req.name().strip(), email, req.signupReason());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 사전 검사 직후 다른 요청이 먼저 저장된 경우에도 필드별 오류로 안내한다.
            checkDuplicates(req.username(), email);
            throw e;
        }
    }

    /** 저장 전·동시 저장 충돌 후에 아이디와 메일의 중복을 확인한다. */
    private void checkDuplicates(String username, String email) {
        if (users.existsByUsername(username)) {
            throw BusinessException.invalid("username", "이미 사용 중인 아이디입니다.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.invalid("email", "이미 사용 중인 메일입니다.");
        }
    }
}
