package com.hankooknongsan.supplier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankooknongsan.supplier.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 마이페이지 API 통합 테스트 (FR-028).
 * 현재 비밀번호 확인과 역할별 접근 권한을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class MyPageApiTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    /** 비밀번호 "원래비번1234"로 로그인 계정을 만든다. */
    void user(String username, Role role) {
        users.save(new AppUser(username, encoder.encode("원래비번1234"), "마이페이지테스터", username + "@test.local", role));
    }

    /** 현재 비밀번호가 맞으면 새 비밀번호로 바뀐다. */
    @Test
    @WithMockUser(username = "mypage-ok", roles = "MANAGER")
    void 현재_비밀번호가_맞으면_바뀐다() throws Exception {
        user("mypage-ok", Role.MANAGER);

        mvc.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"원래비번1234","newPassword":"새비밀번호5678"}"""))
                .andExpect(status().isOk());

        assertThat(encoder.matches("새비밀번호5678", users.findByUsername("mypage-ok").orElseThrow().getPassword())).isTrue();
    }

    /** 현재 비밀번호가 틀리면 바뀌지 않고 400과 칸 오류를 돌려준다. */
    @Test
    @WithMockUser(username = "mypage-wrong", roles = "MANAGER")
    void 현재_비밀번호가_틀리면_바뀌지_않는다() throws Exception {
        user("mypage-wrong", Role.MANAGER);

        mvc.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"틀린비번","newPassword":"새비밀번호5678"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("currentPassword")));

        assertThat(encoder.matches("원래비번1234", users.findByUsername("mypage-wrong").orElseThrow().getPassword())).isTrue();
    }

    /** 조회자(VIEWER)도 업무 데이터 등록·수정과 달리 자기 정보·비밀번호는 바꿀 수 있다. */
    @Test
    @WithMockUser(username = "mypage-viewer", roles = "VIEWER")
    void 조회자도_내_정보는_수정할_수_있다() throws Exception {
        user("mypage-viewer", Role.VIEWER);

        mvc.perform(put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"박조회","email":"viewer2@test.local","notifyEmail":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyEmail").value(false));
    }

    /** 이름·이메일·알림메일 수신 여부를 수정하면 그대로 저장된다. */
    @Test
    @WithMockUser(username = "mypage-profile", roles = "MANAGER")
    void 내_정보를_수정하면_저장된다() throws Exception {
        user("mypage-profile", Role.MANAGER);

        mvc.perform(put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"새이름","email":"new@test.local","notifyEmail":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.email").value("new@test.local"))
                .andExpect(jsonPath("$.notifyEmail").value(false));
    }

    /** 짧은 비밀번호(8자 미만)는 바뀌지 않고 칸 오류를 돌려준다. (리뷰 재현 사례) */
    @Test
    @WithMockUser(username = "mypage-shortpw", roles = "MANAGER")
    void 짧은_비밀번호는_바뀌지_않는다() throws Exception {
        user("mypage-shortpw", Role.MANAGER);

        mvc.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"원래비번1234","newPassword":"1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("newPassword")));

        assertThat(encoder.matches("원래비번1234", users.findByUsername("mypage-shortpw").orElseThrow().getPassword())).isTrue();
    }

    /** 다른 사람이 이미 쓰는 이메일(대소문자만 다름)로는 바꿀 수 없다. (리뷰 재현 사례) */
    @Test
    @WithMockUser(username = "mypage-dupemail", roles = "MANAGER")
    void 중복된_이메일로는_바꿀_수_없다() throws Exception {
        user("mypage-other", Role.MANAGER);
        user("mypage-dupemail", Role.MANAGER);

        mvc.perform(put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"중복테스트","email":"MYPAGE-OTHER@TEST.LOCAL","notifyEmail":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
    }
}
