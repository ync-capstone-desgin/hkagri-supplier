package com.hankooknongsan.supplier.auth;

import com.hankooknongsan.supplier.auth.MyPageDtos.MyProfile;
import com.hankooknongsan.supplier.auth.MyPageDtos.PasswordRequest;
import com.hankooknongsan.supplier.auth.MyPageDtos.ProfileRequest;
import com.hankooknongsan.supplier.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지(내 정보·비밀번호·알림메일 수신) 업무 서비스 (FR-028).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    /** 로그인 아이디로 내 정보를 조회한다. */
    public MyProfile get(String username) {
        return MyProfile.of(find(username));
    }

    /** 이름·이메일·알림메일 수신 여부를 수정한다. 이메일을 바꾸면 다음 알림부터 새 주소로 간다. */
    @Transactional
    public MyProfile updateProfile(String username, ProfileRequest req) {
        AppUser user = find(username);
        user.updateProfile(req.name().trim(), req.email().trim(), req.notifyEmail());
        return MyProfile.of(user);
    }

    /** 현재 비밀번호가 맞을 때만 새 비밀번호로 바꾼다. 틀리면 400(해당 칸 오류)을 던진다. */
    @Transactional
    public void changePassword(String username, PasswordRequest req) {
        AppUser user = find(username);
        if (!encoder.matches(req.currentPassword(), user.getPassword())) {
            throw BusinessException.invalid("currentPassword", "현재 비밀번호가 올바르지 않습니다.");
        }
        user.changePassword(encoder.encode(req.newPassword()));
    }

    /** 로그인 아이디로 사용자를 찾는다. */
    private AppUser find(String username) {
        return users.findByUsername(username).orElseThrow(() -> BusinessException.notFound("사용자"));
    }
}
