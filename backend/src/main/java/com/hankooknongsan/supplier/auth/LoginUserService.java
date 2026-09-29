package com.hankooknongsan.supplier.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 로그인 시 아이디로 사용자를 조회하는 서비스 (스프링 시큐리티 연동).
 */
@Service
@RequiredArgsConstructor
public class LoginUserService implements UserDetailsService {

    private final AppUserRepository users;

    /** 아이디로 사용자를 찾아 로그인 사용자 정보로 반환한다. 없으면 인증 실패 예외를 던진다. */
    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(LoginUser::of)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
