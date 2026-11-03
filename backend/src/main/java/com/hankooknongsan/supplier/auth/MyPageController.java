package com.hankooknongsan.supplier.auth;

import com.hankooknongsan.supplier.auth.MyPageDtos.MyProfile;
import com.hankooknongsan.supplier.auth.MyPageDtos.PasswordRequest;
import com.hankooknongsan.supplier.auth.MyPageDtos.ProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 API: 내 정보 조회·수정, 비밀번호 변경 (FR-028).
 * 로그인한 사람이면 역할(관리자·담당자·조회자)과 무관하게 자기 자신의 정보만 다룬다.
 */
@RestController
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService service;

    /** 로그인한 사용자의 아이디를 세션에서 가져온다. */
    private String me() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** 내 정보를 조회한다. */
    @GetMapping("/api/me/profile")
    public MyProfile profile() {
        return service.get(me());
    }

    /** 이름·이메일·알림메일 수신 여부를 수정한다. */
    @PutMapping("/api/me/profile")
    public MyProfile updateProfile(@Valid @RequestBody ProfileRequest req) {
        return service.updateProfile(me(), req);
    }

    /** 비밀번호를 바꾼다. 현재 비밀번호가 틀리면 400을 돌려준다. */
    @PutMapping("/api/me/password")
    public void changePassword(@Valid @RequestBody PasswordRequest req) {
        service.changePassword(me(), req);
    }
}
