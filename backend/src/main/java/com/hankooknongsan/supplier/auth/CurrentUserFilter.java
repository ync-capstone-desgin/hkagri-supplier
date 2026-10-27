package com.hankooknongsan.supplier.auth;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** URL 권한 검사 전에 DB의 최신 상태·역할을 적용한다. 세션의 오래된 권한을 신뢰하지 않는다. */
@RequiredArgsConstructor
public class CurrentUserFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    /** 비활성 계정은 세션을 끊고, 사용 가능한 계정은 이번 요청의 인증 정보를 새로 만든다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser old) {
            AppUser user = users.findById(old.id()).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            LoginUser current = new LoginUser(user.getId(), user.getUsername(), null,
                    user.getName(), user.getRole(), user.getStatus());
            var refreshed = UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities());
            refreshed.setDetails(authentication.getDetails());
            // 세션에 공유된 컨텍스트를 직접 바꾸지 않아 다른 동시 요청에 영향을 주지 않는다.
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(refreshed);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
