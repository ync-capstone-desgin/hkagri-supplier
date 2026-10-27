package com.hankooknongsan.supplier.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자 저장소 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 로그인 아이디로 사용자를 찾는다. */
    Optional<AppUser> findByUsername(String username);

    /** 아이디 중복을 확인한다. */
    boolean existsByUsername(String username);

    /** 이메일은 대소문자를 구분하지 않고 중복을 확인한다. */
    boolean existsByEmailIgnoreCase(String email);

    /** 본인을 제외하고 이메일 중복을 확인한다. (마이페이지에서 내 정보 수정용) */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** 실제 이용 가능한 계정만 담당자 선택 목록에 표시한다. */
    List<AppUser> findByStatusOrderById(UserStatus status);

    /** 특정 역할의 사용자 목록을 찾는다. (예: 담당자가 없는 알림을 관리자에게 보낼 때) */
    List<AppUser> findByRole(Role role);
}
