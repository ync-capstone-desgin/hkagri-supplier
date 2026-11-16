package com.hankooknongsan.supplier.auth;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인·내 정보·사용자 목록 API.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AppUserRepository users;

    /** 로그인 요청: 아이디와 비밀번호 */
    public record LoginRequest(@NotBlank(message = "아이디를 입력해 주세요.") String username,
                               @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
    }

    /** 로그인한 사용자 정보 응답 */
    public record Me(Long id, String username, String name, Role role) {
        /** 로그인 사용자 정보에서 응답을 만든다. */
        static Me of(LoginUser u) {
            return new Me(u.id(), u.username(), u.name(), u.role());
        }
    }

    /** 담당자 선택상자에 쓰는 사용자 요약 */
    public record UserOption(Long id, String name, Role role, String email) {
    }

    /**
     * 아이디·비밀번호로 로그인하고, 성공하면 세션에 로그인 정보를 저장한다.
     * 실패하면 {@link AuthExceptionHandler}가 401을 돌려준다.
     */
    @PostMapping("/api/auth/login")
    public Me login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return Me.of((LoginUser) auth.getPrincipal());
    }

    /** 현재 로그인한 사용자 정보를 반환한다. 화면 새로고침 시 로그인 유지 여부 확인에 쓴다. */
    @GetMapping("/api/auth/me")
    public Me me(@AuthenticationPrincipal LoginUser user) {
        return Me.of(user);
    }

    /** 전체 사용자 목록을 반환한다. 담당자 선택상자에 쓴다. */
    @GetMapping("/api/users")
    public List<UserOption> users() {
        return users.findByStatusOrderById(UserStatus.ACTIVE).stream()
                .map(u -> new UserOption(u.getId(), u.getName(), u.getRole(), u.getEmail()))
                .toList();
    }
}
