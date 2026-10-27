package com.hankooknongsan.supplier.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 세션에 저장되는 로그인 사용자 정보.
 * 엔티티를 세션에 넣지 않고 인증·권한 판단에 필요한 값만 담는다.
 */
public record LoginUser(Long id, String username, String password, String name, Role role, UserStatus status)
        implements UserDetails, Serializable {

    /** 사용자 엔티티에서 로그인 사용자 정보를 만든다. */
    static LoginUser of(AppUser u) {
        return new LoginUser(u.getId(), u.getUsername(), u.getPassword(), u.getName(), u.getRole(), u.getStatus());
    }

    /** 역할을 스프링 시큐리티 권한(ROLE_ADMIN 등)으로 변환해 반환한다. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** 암호화된 비밀번호를 반환한다. (로그인 검증용) */
    @Override
    public String getPassword() {
        return password;
    }

    /** 로그인 아이디를 반환한다. */
    @Override
    public String getUsername() {
        return username;
    }
}
