package com.hankooknongsan.supplier.auth;

import java.util.Locale;

import com.hankooknongsan.supplier.auth.MyPageDtos.MyProfile;
import com.hankooknongsan.supplier.auth.MyPageDtos.PasswordRequest;
import com.hankooknongsan.supplier.auth.MyPageDtos.ProfileRequest;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * 이름·이메일·알림메일 수신 여부를 수정한다. 이메일을 바꾸면 다음 알림부터 새 주소로 간다.
     * 이메일은 가입(SignupService)과 같이 소문자로 통일해 저장하고, 본인을 제외한 중복을 막는다.
     * 저장 직후 flush해 동시 저장 충돌(DataIntegrityViolationException)도 같은 자리에서 칸 오류로 바꾼다.
     */
    @Transactional
    public MyProfile updateProfile(String username, ProfileRequest req) {
        AppUser user = find(username);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        checkEmailDuplicate(user.getId(), email);
        user.updateProfile(req.name().trim(), email, req.notifyEmail());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            checkEmailDuplicate(user.getId(), email);
            throw e;
        }
        return MyProfile.of(user);
    }

    /** 현재 비밀번호가 맞을 때만 새 비밀번호로 바꾼다. 틀리면 400(해당 칸 오류)을 던진다. */
    @Transactional
    public void changePassword(String username, PasswordRequest req) {
        AppUser user = find(username);
        if (!encoder.matches(req.currentPassword(), user.getPassword())) {
            throw BusinessException.invalid("currentPassword", "현재 비밀번호가 올바르지 않습니다.");
        }
        PasswordPolicy.checkByteLength("newPassword", req.newPassword());
        user.changePassword(encoder.encode(req.newPassword()));
    }

    /** 로그인 아이디로 사용자를 찾는다. */
    private AppUser find(String username) {
        return users.findByUsername(username).orElseThrow(() -> BusinessException.notFound("사용자"));
    }

    /** 본인을 제외한 다른 사용자가 이미 쓰는 이메일이면 칸 오류를 던진다. */
    private void checkEmailDuplicate(Long userId, String email) {
        if (users.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw BusinessException.invalid("email", "이미 사용 중인 메일입니다.");
        }
    }
}
