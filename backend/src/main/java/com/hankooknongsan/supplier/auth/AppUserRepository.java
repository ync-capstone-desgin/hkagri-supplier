package com.hankooknongsan.supplier.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

/** 사용자 저장소 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 로그인 아이디로 사용자를 찾는다. */
    Optional<AppUser> findByUsername(String username);

    /** 아이디 중복을 확인한다. */
    boolean existsByUsername(String username);

    /** 이메일은 대소문자를 구분하지 않고 중복을 확인한다. */
    boolean existsByEmailIgnoreCase(String email);

    /** 실제 이용 가능한 계정만 담당자 선택 목록에 표시한다. */
    List<AppUser> findByStatusOrderById(UserStatus status);

    /** 역할과 계정 상태로 수신자를 찾아 비활성 관리자에게 알림이 생성되지 않게 한다. */
    List<AppUser> findByRoleAndStatus(Role role, UserStatus status);

    /** 관리자 변경을 같은 순서로 잠가 동시 요청으로 관리자가 모두 사라지는 것을 막는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.role = com.hankooknongsan.supplier.auth.Role.ADMIN and u.status = com.hankooknongsan.supplier.auth.UserStatus.ACTIVE order by u.id")
    List<AppUser> lockActiveAdmins();

    /** 승인·거절 등이 동시에 들어와도 대상 계정을 한 번에 하나씩 변경한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findForUpdateById(Long id);
}
