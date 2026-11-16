package com.hankooknongsan.supplier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 실제 PostgreSQL로 가입·중복·비밀번호 저장·승인 상태에 따른 로그인 차단을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SignupApiTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    /** 가입해도 세션이 생기지 않으며 승인 전에는 올바른 비밀번호로도 로그인할 수 없다. */
    @Test
    void 가입은_승인대기로_저장하고_비밀번호는_암호화한다() throws Exception {
        String username = username();
        signup(username, username + "@example.com", "testpass123", "testpass123")
                .andExpect(status().isCreated());
        AppUser user = users.findByUsername(username).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getRole()).isEqualTo(Role.VIEWER);
        assertThat(user.getSignupReason()).isEqualTo("업무용");
        assertThat(user.getPassword()).isNotEqualTo("testpass123");
        assertThat(encoder.matches("testpass123", user.getPassword())).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        login(username, "testpass123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("승인 대기 중")))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
        login(username, "wrongpassword").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("아이디 또는 비밀번호가 올바르지 않습니다."));
        assertThat(users.findByStatusOrderById(UserStatus.ACTIVE))
                .extracting(AppUser::getId).doesNotContain(user.getId());
    }

    /** 아이디 및 대소문자만 다른 메일도 중복 등록하지 못하고 필드 오류가 반환된다. */
    @Test
    void 중복_아이디와_메일은_입력칸_오류를_반환한다() throws Exception {
        String username = username();
        signup(username, username + "@example.com", "testpass123", "testpass123").andExpect(status().isCreated());
        signup(username, "other@example.com", "testpass123", "testpass123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("username")));
        signup(username(), username.toUpperCase(java.util.Locale.ROOT) + "@EXAMPLE.COM", "testpass123", "testpass123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
    }

    /** 짧은 비밀번호·확인 불일치·bcrypt 바이트 제한을 서버에서도 거절한다. */
    @Test
    void 잘못된_비밀번호는_저장하지_않는다() throws Exception {
        String username = username();
        signup(username, username + "@example.com", "short", "short")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
        signup(username, username + "@example.com", "testpass123", "different")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("passwordConfirm")));
        signup(username, username + "@example.com", "가".repeat(25), "가".repeat(25))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
        assertThat(users.findByUsername(username)).isEmpty();
    }

    /** 기존 ACTIVE 계정은 로그인할 수 있고 거절·비활성 상태는 세션 발급 전에 차단한다. */
    @Test
    void 사용중인_계정만_로그인할_수_있다() throws Exception {
        String username = username();
        users.saveAndFlush(new AppUser(username, encoder.encode("testpass123"), "테스트", username + "@example.com", Role.VIEWER));
        login(username, "testpass123").andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
        for (String state : new String[]{"REJECTED", "INACTIVE"}) {
            jdbc.update("update app_user set status = ? where username = ?", state, username);
            login(username, "testpass123").andExpect(status().isUnauthorized());
        }
    }

    /** 테스트끼리 데이터가 겹치지 않는 아이디를 만든다. */
    private String username() {
        return "signup_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** 익명 가입 요청을 보낸다. 테스트 입력은 JSON 이스케이프가 필요 없는 값만 사용한다. */
    private ResultActions signup(String username, String email, String password, String confirm) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"테스트", "username":"%s", "email":"%s", "password":"%s",
                 "passwordConfirm":"%s", "signupReason":"업무용"}
                """.formatted(username, email, password, confirm)));
    }

    /** 세션을 재사용하지 않고 로그인 요청을 보낸다. */
    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"%s", "password":"%s"}
                """.formatted(username, password)));
    }
}
