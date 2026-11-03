package com.hankooknongsan.supplier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.contract.Contract;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 실제 로그인 세션으로 관리자 권한·상태 전이·동시 변경을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class UserAdminApiTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired UserAdminService service;
    @Autowired SupplierRepository suppliers;
    @Autowired ContractRepository contracts;
    @Autowired JdbcTemplate jdbc;

    /** 승인하면 선택 역할로 로그인되고 거절하면 로그인할 수 없으며 재처리는 차단된다. */
    @Test
    void 승인과_거절은_대기_계정에만_적용된다() throws Exception {
        var admin = login(user(Role.ADMIN, false));
        var pending = user(Role.VIEWER, true);
        mvc.perform(get("/api/admin/users").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").isNumber())
                .andExpect(jsonPath("$.users[0].password").doesNotExist());
        change(admin, pending, "APPROVE", null).andExpect(status().isBadRequest());
        change(admin, pending, "APPROVE", "MANAGER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        login(pending);
        change(admin, pending, "REJECT", null).andExpect(status().isConflict());
        var rejected = user(Role.VIEWER, true);
        change(admin, rejected, "REJECT", null).andExpect(status().isOk());
        loginRequest(rejected).andExpect(status().isUnauthorized());
        change(admin, rejected, "REACTIVATE", null).andExpect(status().isConflict());
    }

    /** 같은 로그인 세션을 유지해도 역할을 바꾸면 다음 요청의 읽기·쓰기·관리 권한이 바뀐다. */
    @Test
    void 기존_세션에도_다음_요청부터_새_권한을_적용한다() throws Exception {
        var admin = login(user(Role.ADMIN, false));
        var target = user(Role.MANAGER, false);
        var session = login(target);
        change(admin, target, "CHANGE_ROLE", "VIEWER").andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.role").value("VIEWER"));
        mvc.perform(post("/api/suppliers").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"권한검증\",\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        change(admin, target, "CHANGE_ROLE", "ADMIN").andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk());
        change(admin, target, "CHANGE_ROLE", "VIEWER").andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    /** 비활성화는 기존 세션과 신규 로그인을 막지만 업체·계약 담당자 FK는 유지한다. */
    @Test
    void 비활성화와_재사용은_업무_연결을_보존한다() throws Exception {
        var admin = login(user(Role.ADMIN, false));
        var target = user(Role.MANAGER, false);
        var session = login(target);
        Supplier supplier = new Supplier("담당유지검증", SupplierStatus.ACTIVE);
        supplier.update("담당유지검증", null, SupplierStatus.ACTIVE, null, null, null, target, null);
        supplier = suppliers.saveAndFlush(supplier);
        Contract contract = new Contract(supplier);
        contract.update(supplier, "test-" + UUID.randomUUID(), "담당유지", LocalDate.now(), LocalDate.now().plusDays(30), false, target, null, null);
        contract = contracts.saveAndFlush(contract);
        change(admin, target, "DEACTIVATE", null).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        loginRequest(target).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select owner_id from supplier where id = ?", Long.class, supplier.getId())).isEqualTo(target.getId());
        assertThat(jdbc.queryForObject("select owner_id from contract where id = ?", Long.class, contract.getId())).isEqualTo(target.getId());
        change(admin, target, "REACTIVATE", null).andExpect(status().isOk());
        login(target);
    }

    /** 관리자 본인의 역할·상태 변경은 서버에서도 거절한다. */
    @Test
    void 자기_계정은_변경할_수_없다() throws Exception {
        var admin = user(Role.ADMIN, false);
        var session = login(admin);
        change(session, admin, "CHANGE_ROLE", "VIEWER").andExpect(status().isBadRequest());
        change(session, admin, "DEACTIVATE", null).andExpect(status().isBadRequest());
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    /** 익명·담당자·조회자는 목록과 변경 API 모두 접근할 수 없다. */
    @Test
    void 관리자_외에는_목록과_변경을_차단한다() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        var target = user(Role.VIEWER, true);
        for (Role role : new Role[]{Role.MANAGER, Role.VIEWER}) {
            var session = login(user(role, false));
            mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
            change(session, target, "APPROVE", "ADMIN").andExpect(status().isForbidden());
        }
    }

    /** 두 관리자가 동시에 서로 강등해도 잠금 뒤 권한을 재확인해 한 명은 남긴다. */
    @Test
    void 동시_상호_강등으로_관리자가_모두_사라지지_않는다() throws Exception {
        var a = user(Role.ADMIN, false);
        var b = user(Role.ADMIN, false);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> demoteAfter(start, a, b));
            var second = pool.submit(() -> demoteAfter(start, b, a));
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(users.findAllById(java.util.List.of(a.getId(), b.getId())).stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE).count()).isEqualTo(1);
    }

    /** 동시에 시작한 변경 중 권한을 잃은 관리자의 요청은 실패로 센다. */
    private int demoteAfter(CountDownLatch start, AppUser actor, AppUser target) throws InterruptedException {
        start.await();
        try {
            service.change(actor.getId(), target.getId(), new UserAdminController.ChangeRequest(UserAdminController.Action.CHANGE_ROLE, Role.VIEWER));
            return 1;
        } catch (AccessDeniedException e) {
            return 0;
        }
    }

    /** 테스트 간 충돌하지 않는 가상 계정을 만든다. */
    private AppUser user(Role role, boolean pending) {
        String name = "admin_" + UUID.randomUUID().toString().replace("-", "");
        String hash = encoder.encode("testpass123");
        return users.saveAndFlush(pending ? AppUser.pending(name, hash, "테스트", name + "@example.com", "검증")
                : new AppUser(name, hash, "테스트", name + "@example.com", role));
    }

    /** 실제 로그인 API를 호출한다. */
    private ResultActions loginRequest(AppUser user) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"testpass123\"}"));
    }

    /** 인증 성공 세션을 반환해 이후 요청에도 동일하게 사용한다. */
    private MockHttpSession login(AppUser user) throws Exception {
        return (MockHttpSession) loginRequest(user).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    /** 관리자 세션으로 대상 계정 작업을 요청한다. */
    private ResultActions change(MockHttpSession session, AppUser target, String action, String role) throws Exception {
        return mvc.perform(post("/api/admin/users/" + target.getId() + "/actions").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\""
                        + (role == null ? "" : ",\"role\":\"" + role + "\"") + "}"));
    }
}
