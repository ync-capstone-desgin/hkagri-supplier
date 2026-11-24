package com.hankooknongsan.supplier.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 비밀번호 재설정 토큰 저장소 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** 메일 링크의 토큰 값으로 찾는다. */
    Optional<PasswordResetToken> findByToken(String token);
}
