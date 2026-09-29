package com.hankooknongsan.supplier.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자 저장소 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 로그인 아이디로 사용자를 찾는다. */
    Optional<AppUser> findByUsername(String username);

    /** 특정 역할의 사용자 목록을 찾는다. (예: 담당자가 없는 알림을 관리자에게 보낼 때) */
    List<AppUser> findByRole(Role role);
}
