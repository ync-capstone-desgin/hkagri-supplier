package com.hankooknongsan.supplier.auth;

import com.hankooknongsan.supplier.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hankooknongsan.supplier.auth.UserAdminController.*;

/** 승인 전이와 관리자 보호 규칙을 한 트랜잭션 안에서 검사한다. */
@Service
@RequiredArgsConstructor
public class UserAdminService {
    private final AppUserRepository users;

    /** 계정을 최신 가입 순으로 조회하고 승인 대기 수를 계산한다. */
    @Transactional(readOnly = true)
    public UserList list() {
        var items = users.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().map(UserItem::of).toList();
        return new UserList(items.stream().filter(u -> u.status() == UserStatus.PENDING).count(), items);
    }

    /** 관리자들을 먼저 잠근 뒤 대상 계정을 잠가 동시 승인·권한 변경도 안전하게 처리한다. */
    @Transactional
    public UserItem change(Long actorId, Long targetId, ChangeRequest request) {
        var admins = users.lockActiveAdmins();
        if (admins.stream().noneMatch(u -> u.getId().equals(actorId))) {
            throw new AccessDeniedException("현재 관리자만 변경할 수 있습니다.");
        }
        if (actorId.equals(targetId)) {
            throw BusinessException.invalid("id", "자기 계정의 역할이나 상태는 변경할 수 없습니다.");
        }
        AppUser target = users.findForUpdateById(targetId).orElseThrow(() -> BusinessException.notFound("사용자"));
        Role role = target.getRole();
        UserStatus status = target.getStatus();
        switch (request.action()) {
            case APPROVE -> {
                requireStatus(target, UserStatus.PENDING);
                role = requireRole(request);
                status = UserStatus.ACTIVE;
            }
            case REJECT -> { requireStatus(target, UserStatus.PENDING); status = UserStatus.REJECTED; }
            case CHANGE_ROLE -> { requireStatus(target, UserStatus.ACTIVE); role = requireRole(request); }
            case DEACTIVATE -> { requireStatus(target, UserStatus.ACTIVE); status = UserStatus.INACTIVE; }
            case REACTIVATE -> { requireStatus(target, UserStatus.INACTIVE); status = UserStatus.ACTIVE; }
        }
        if (target.getRole() == Role.ADMIN && target.getStatus() == UserStatus.ACTIVE
                && (role != Role.ADMIN || status != UserStatus.ACTIVE) && admins.size() <= 1) {
            throw BusinessException.invalid("role", "사용 가능한 관리자는 최소 한 명이어야 합니다.");
        }
        target.changeAccess(role, status);
        return UserItem.of(target);
    }

    /** 이미 다른 관리자가 처리한 요청이나 허용하지 않은 상태 전이는 거절한다. */
    private void requireStatus(AppUser user, UserStatus expected) {
        if (user.getStatus() != expected) {
            throw new BusinessException(HttpStatus.CONFLICT, "계정 상태가 변경되었습니다. 목록을 새로고침해 주세요.");
        }
    }

    /** 승인·역할 변경 요청에서 역할 선택을 필수로 검사한다. */
    private Role requireRole(ChangeRequest request) {
        if (request.role() == null) throw BusinessException.invalid("role", "역할을 선택해 주세요.");
        return request.role();
    }
}
