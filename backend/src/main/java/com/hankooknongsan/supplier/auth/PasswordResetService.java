package com.hankooknongsan.supplier.auth;

import java.security.SecureRandom;
import java.util.Base64;

import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기·재설정 (FR-029).
 * 계정 존재 여부를 숨기기 위해, 요청 처리는 계정이 없거나 비활성이어도 예외 없이 조용히 끝낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    /** 재설정 링크의 유효 시간(분). */
    private static final long VALID_MINUTES = 30;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JavaMailSender mailSender;
    private final AppClock clock;

    /** 발신 주소 */
    @Value("${app.mail-from}")
    private String from;

    /** 메일 속 재설정 링크에 쓸 화면 주소 */
    @Value("${app.web-url}")
    private String webUrl;

    /**
     * 아이디 또는 이메일로 계정을 찾아 재설정 메일을 보낸다.
     * 계정이 없거나, 아직 승인 전이거나, 등록된 이메일이 없으면 아무 일도 하지 않는다(호출한 쪽은 몰라야 한다).
     * 메일 발송 자체가 실패해도(개발 환경 등) 요청 처리는 그대로 끝낸다.
     */
    @Transactional
    public void request(String usernameOrEmail) {
        AppUser user = find(usernameOrEmail);
        if (user == null || user.getStatus() != UserStatus.ACTIVE
                || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String token = newToken();
        tokens.save(new PasswordResetToken(user, token, clock.now().plusMinutes(VALID_MINUTES)));
        try {
            send(user, token);
        } catch (MailException e) {
            log.warn("비밀번호 재설정 메일을 보내지 못했습니다: {}", user.getUsername(), e);
        }
    }

    /** 유효한 토큰이면 새 비밀번호로 바꾸고, 다시 쓸 수 없게 사용 처리한다. */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken found = tokens.findByToken(token)
                .filter(t -> t.isUsable(clock.now()))
                .orElseThrow(() -> BusinessException.invalid("token", "유효하지 않거나 이미 지난 링크입니다. 비밀번호 찾기를 다시 시도해 주세요."));
        PasswordPolicy.checkByteLength("newPassword", newPassword);
        found.getUser().changePassword(encoder.encode(newPassword));
        found.use(clock.now());
    }

    /** 아이디로 먼저 찾고, 없으면 이메일로 찾는다. 둘 다 없으면 null. */
    private AppUser find(String usernameOrEmail) {
        return users.findByUsername(usernameOrEmail)
                .or(() -> users.findByEmailIgnoreCase(usernameOrEmail))
                .orElse(null);
    }

    /** 추측할 수 없는 URL-safe 토큰을 만든다 (256비트 무작위). */
    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 재설정 링크를 담은 메일을 보낸다. */
    private void send(AppUser user, String token) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(user.getEmail());
        mail.setSubject("[한국농산] 비밀번호 재설정");
        mail.setText("""
                안녕하세요, %s님.
                한국농산 협력업체 통합관리 시스템입니다.

                아래 링크에서 새 비밀번호를 설정해 주세요. 이 링크는 30분 동안, 한 번만 사용할 수 있습니다.

                %s/reset-password?token=%s

                본인이 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.

                한국농산 협력업체 통합관리 시스템 드림
                """.formatted(user.getName(), webUrl, token));
        mailSender.send(mail);
    }
}
