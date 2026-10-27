package com.hankooknongsan.supplier.auth;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 마이페이지 API 요청·응답 모음 (FR-028). */
public class MyPageDtos {

    private MyPageDtos() {
    }

    /** 내 정보 조회 응답 */
    public record MyProfile(Long id, String username, String name, String email, Role role, boolean notifyEmail,
                            LocalDateTime createdAt) {
        /** 사용자 엔티티에서 응답을 만든다. */
        static MyProfile of(AppUser u) {
            return new MyProfile(u.getId(), u.getUsername(), u.getName(), u.getEmail(), u.getRole(),
                    u.isNotifyEmail(), u.getCreatedAt());
        }
    }

    /** 내 정보 수정 요청 */
    public record ProfileRequest(
            @NotBlank(message = "이름을 입력해 주세요.") String name,
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
            boolean notifyEmail) {
    }

    /** 비밀번호 변경 요청 */
    public record PasswordRequest(
            @NotBlank(message = "현재 비밀번호를 입력해 주세요.") String currentPassword,
            @NotBlank(message = "새 비밀번호를 입력해 주세요.") String newPassword) {
    }
}
