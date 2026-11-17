package com.hankooknongsan.supplier.auth;

import java.time.LocalDateTime;

import com.hankooknongsan.supplier.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호 재설정 1회용 토큰 (FR-029).
 * 메일로 보낸 재설정 링크의 유효성을 이 행으로 검증한다. 사용하거나 기한이 지나면 다시 쓸 수 없다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id")
    private AppUser user;

    /** 추측할 수 없는 URL-safe 임의 문자열. 메일 속 링크의 쿼리 파라미터로 그대로 쓰인다. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;

    /** 유효 기한을 정해 새 토큰을 만든다. */
    PasswordResetToken(AppUser user, String token, LocalDateTime expiresAt) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    /** 기한이 지났거나 이미 썼으면 더 이상 쓸 수 없다. */
    boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    /** 비밀번호를 바꾸는 데 썼다고 기록한다. 이후로는 재사용할 수 없다. */
    void use(LocalDateTime now) {
        this.usedAt = now;
    }
}
