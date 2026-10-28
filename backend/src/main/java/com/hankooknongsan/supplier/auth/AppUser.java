package com.hankooknongsan.supplier.auth;

import com.hankooknongsan.supplier.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시스템 사용자(내부 담당자) 엔티티.
 * 로그인 계정이면서 협력업체·계약·인증의 "담당자"와 알림 수신자로 쓰인다.
 */
@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디 */
    private String username;
    /** 암호화된 비밀번호 ({bcrypt} 접두어 포함) */
    private String password;
    /** 이름(화면 표시용) */
    private String name;
    /** 이메일 (알림 수신용) */
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    /** 가입 신청 사유 (선택 입력) */
    private String signupReason;

    /** 관리자 서비스가 검증한 역할·계정 상태만 변경한다. 업무 담당 연결은 유지한다. */
    void changeAccess(Role role, UserStatus status) {
        this.role = role;
        this.status = status;
    }

    /** 공개 가입 신청은 승인 대기·최소 권한으로 만들며 역할을 입력받지 않는다. */
    public static AppUser pending(String username, String password, String name, String email, String reason) {
        AppUser user = new AppUser(username, password, name, email, Role.VIEWER);
        user.status = UserStatus.PENDING;
        user.signupReason = reason;
        return user;
    }

    /** 새 사용자를 만든다. 비밀번호는 암호화된 값을 넘겨야 한다. */
    public AppUser(String username, String password, String name, String email, Role role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.role = role;
    }
}
