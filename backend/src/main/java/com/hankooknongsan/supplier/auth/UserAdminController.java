package com.hankooknongsan.supplier.auth;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 사용자 목록과 계정 상태 변경 API. 비밀번호 해시는 응답하지 않는다. */
@RestController
@RequiredArgsConstructor
public class UserAdminController {
    private final UserAdminService service;

    /** 관리자가 수행할 수 있는 작업. 임의의 상태 지정 대신 허용된 전이만 제공한다. */
    public enum Action { APPROVE, REJECT, CHANGE_ROLE, DEACTIVATE, REACTIVATE }

    /** 승인·역할 변경일 때 role이 필요하다. */
    public record ChangeRequest(@NotNull(message = "작업을 선택해 주세요.") Action action, Role role) { }

    /** 관리 화면에 필요한 계정 정보. */
    public record UserItem(Long id, String username, String name, String email, Role role,
                           UserStatus status, String signupReason, LocalDateTime createdAt) {
        /** 엔티티에서 민감한 인증 정보를 제외한다. */
        static UserItem of(AppUser user) {
            return new UserItem(user.getId(), user.getUsername(), user.getName(), user.getEmail(),
                    user.getRole(), user.getStatus(), user.getSignupReason(), user.getCreatedAt());
        }
    }

    /** 승인 대기 인원과 전체 계정 목록을 함께 반환한다. */
    public record UserList(long pendingCount, List<UserItem> users) { }

    /** 전체 사용자 목록을 읽는다. URL 권한 검사로 관리자만 접근할 수 있다. */
    @GetMapping("/api/admin/users")
    public UserList list() {
        return service.list();
    }

    /** 승인·거절·역할 변경·비활성화·재사용을 처리한다. */
    @PostMapping("/api/admin/users/{id}/actions")
    public UserItem change(@PathVariable Long id, @Valid @RequestBody ChangeRequest request,
                           @AuthenticationPrincipal LoginUser actor) {
        return service.change(actor.id(), id, request);
    }
}
