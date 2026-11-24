package com.hankooknongsan.supplier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hankooknongsan.supplier.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 비밀번호 찾기·재설정을 실제 PostgreSQL로 검증한다. 메일 발송 자체는 개발 환경에만 의존하므로 토큰 생성 결과로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class PasswordResetApiTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    /** 테스트끼리 겹치지 않는 활성 계정을 만든다. */
    private AppUser user() {
        String name = "reset_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new AppUser(name, encoder.encode("원래비번1234"), "테스트", name + "@example.com", Role.MANAGER));
    }

    /** 활성 계정의 아이디로 요청하면 그 계정에 쓸 수 있는 토큰이 생긴다. */
    @Test
    void 존재하는_계정으로_요청하면_토큰이_생긴다() throws Exception {
        AppUser u = user();
        forgot(u.getUsername()).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("해당 계정이 있으면 비밀번호 재설정 메일을 보냈습니다."));
        var saved = tokens.findAll().stream().filter(t -> t.getUser().getId().equals(u.getId())).findFirst().orElseThrow();
        assertThat(saved.isUsable(LocalDateTime.now())).isTrue();
    }

    /** 아이디 대신 이메일로도 같은 계정을 찾는다(대소문자 무시). */
    @Test
    void 이메일로도_요청할_수_있다() throws Exception {
        AppUser u = user();
        long before = tokens.count();
        forgot(u.getEmail().toUpperCase(java.util.Locale.ROOT)).andExpect(status().isOk());
        assertThat(tokens.count()).isEqualTo(before + 1);
    }

    /** 없는 계정·승인 대기 계정 모두 같은 응답이며, 토큰도 만들지 않는다. */
    @Test
    void 없거나_비활성인_계정은_토큰을_만들지_않지만_응답은_같다() throws Exception {
        long before = tokens.count();
        forgot("no-such-user-" + UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("해당 계정이 있으면 비밀번호 재설정 메일을 보냈습니다."));
        AppUser pending = users.saveAndFlush(AppUser.pending("reset_pending_" + UUID.randomUUID(),
                encoder.encode("원래비번1234"), "테스트", "pending-" + UUID.randomUUID() + "@example.com", null));
        forgot(pending.getUsername()).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("해당 계정이 있으면 비밀번호 재설정 메일을 보냈습니다."));
        assertThat(tokens.count()).isEqualTo(before);
    }

    /** 올바른 토큰으로 바꾸면 그 비밀번호로 로그인되고, 같은 토큰은 다시 쓸 수 없다. */
    @Test
    void 토큰으로_비밀번호를_바꾸면_새_비밀번호로_로그인된다() throws Exception {
        AppUser u = user();
        forgot(u.getUsername()).andExpect(status().isOk());
        String token = tokens.findAll().stream().filter(t -> t.getUser().getId().equals(u.getId())).findFirst().orElseThrow().getToken();

        reset(token, "새비밀번호5678").andExpect(status().isNoContent());
        login(u.getUsername(), "새비밀번호5678").andExpect(status().isOk());

        reset(token, "또다른비번5678").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("token")));
    }

    /** 기한이 지난 토큰은 거절한다. */
    @Test
    void 기한이_지난_토큰은_거절한다() throws Exception {
        AppUser u = user();
        forgot(u.getUsername()).andExpect(status().isOk());
        String token = tokens.findAll().stream().filter(t -> t.getUser().getId().equals(u.getId())).findFirst().orElseThrow().getToken();
        jdbc.update("update password_reset_token set expires_at = ? where token = ?",
                LocalDateTime.now().minusMinutes(1), token);

        reset(token, "새비밀번호5678").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("token")));
    }

    /** 없는 토큰·너무 짧은 새 비밀번호는 400이다. */
    @Test
    void 없는_토큰과_짧은_새_비밀번호는_거절한다() throws Exception {
        AppUser u = user();
        forgot(u.getUsername()).andExpect(status().isOk());
        String token = tokens.findAll().stream().filter(t -> t.getUser().getId().equals(u.getId())).findFirst().orElseThrow().getToken();

        reset("no-such-token", "새비밀번호5678").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("token")));
        reset(token, "1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("newPassword")));
    }

    /** 익명 요청을 보낸다. */
    private ResultActions forgot(String usernameOrEmail) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.anonymous())
                .content("{\"usernameOrEmail\":\"%s\"}".formatted(usernameOrEmail)));
    }

    /** 익명 요청을 보낸다. */
    private ResultActions reset(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.anonymous())
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
    }

    /** 세션을 재사용하지 않고 로그인 요청을 보낸다. */
    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"%s", "password":"%s"}
                """.formatted(username, password)));
    }
}
