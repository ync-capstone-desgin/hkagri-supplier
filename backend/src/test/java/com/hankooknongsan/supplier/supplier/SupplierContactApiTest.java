package com.hankooknongsan.supplier.supplier;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.common.AppClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 협력업체 추가 담당자·소통 메모 API 통합 테스트 (#49).
 * 담당자를 여러 명 등록·수정·삭제하고 변경 이력에 남는지, 메모가 소통한 날짜순으로 보이는지,
 * 메모는 작성자 본인과 관리자만 지울 수 있는지, 조회자는 보기만 되는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class SupplierContactApiTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired AppClock clock;

    Long supplierId;
    Long otherSupplierId;

    /** 테스트마다 협력업체 두 곳을 만들고 기준일을 고정한다. */
    @BeforeEach
    void setUp() {
        clock.setToday(TODAY);
        supplierId = suppliers.save(new Supplier("담당자 테스트 업체", SupplierStatus.ACTIVE)).getId();
        otherSupplierId = suppliers.save(new Supplier("다른 업체", SupplierStatus.ACTIVE)).getId();
    }

    /** 다른 테스트에 영향을 주지 않게 기준일을 되돌린다. */
    @AfterEach
    void tearDown() {
        clock.setToday(null);
    }

    /** 응답 본문에서 id를 꺼낸다. */
    long idOf(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("id").asLong();
    }

    /** 담당자 추가 요청을 보낸다. */
    ResultActions addContact(String json) throws Exception {
        return mvc.perform(post("/api/suppliers/{id}/contacts", supplierId).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 메모 작성 요청을 보낸다. */
    ResultActions addNote(String date, String content) throws Exception {
        return mvc.perform(post("/api/suppliers/{id}/notes", supplierId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactDate\":\"%s\",\"content\":\"%s\"}".formatted(date, content)));
    }

    /** 담당자를 여러 명 등록·수정·삭제할 수 있고, 그 내용이 업체의 변경 이력에 남는다. */
    @Test
    void contactsCanBeAddedUpdatedDeletedAndAreLogged() throws Exception {
        long first = idOf(addContact("""
                {"name":"홍길동","title":"품질팀 과장","phone":"010-1111-2222","email":"hong@example.com"}"""));
        long second = idOf(addContact("{\"name\":\"김영희\",\"title\":\"\"}"));

        mvc.perform(put("/api/suppliers/{id}/contacts/{c}", supplierId, first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\",\"title\":\"품질팀 차장\",\"phone\":\"010-1111-2222\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("품질팀 차장"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(delete("/api/suppliers/{id}/contacts/{c}", supplierId, second)).andExpect(status().isNoContent());

        mvc.perform(get("/api/suppliers/{id}/contacts", supplierId)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("홍길동")).andExpect(jsonPath("$[0].title").value("품질팀 차장"));
        mvc.perform(get("/api/suppliers/{id}/changes", supplierId)).andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[*].changes[0].label", contains("담당자 삭제", "담당자 수정", "담당자 추가", "담당자 추가")))
                .andExpect(jsonPath("$.content[0].changes[0].before").value("김영희"))
                .andExpect(jsonPath("$.content[1].changes[0].before").value("홍길동 · 품질팀 과장 · 010-1111-2222 · hong@example.com"))
                .andExpect(jsonPath("$.content[1].changes[0].after").value("홍길동 · 품질팀 차장 · 010-1111-2222"));
    }

    /** 이름이 없거나 이메일 형식이 틀리면 저장하지 않고, 다른 업체의 담당자 ID로는 고치거나 지울 수 없다. */
    @Test
    void contactValidationAndOwnership() throws Exception {
        addContact("{\"name\":\" \"}").andExpect(status().isBadRequest());
        addContact("{\"name\":\"홍길동\",\"email\":\"메일아님\"}").andExpect(status().isBadRequest());
        long id = idOf(addContact("{\"name\":\"홍길동\"}"));

        mvc.perform(put("/api/suppliers/{id}/contacts/{c}", otherSupplierId, id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"바꿈\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/suppliers/{id}/contacts/{c}", otherSupplierId, id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/suppliers/{id}/contacts", 999_999_999L)).andExpect(status().isNotFound());
    }

    /** 메모는 소통한 날짜가 최근인 순으로 보이고, 작성자와 이름이 남는다. 오늘 이후 날짜와 빈 내용은 막는다. */
    @Test
    void notesAreOrderedByContactDate() throws Exception {
        addNote("2026-11-10", "단가 협의 통화").andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("manager1")).andExpect(jsonPath("$.deletable").value(true));
        addNote("2026-11-17", "납기 일정 메일 회신").andExpect(status().isCreated());
        addNote("2026-11-03", "샘플 요청").andExpect(status().isCreated());
        addNote("2026-11-18", "내일 일").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("contactDate"));
        addNote("2026-11-17", " ").andExpect(status().isBadRequest());

        mvc.perform(get("/api/suppliers/{id}/notes", supplierId)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].contactDate", contains("2026-11-17", "2026-11-10", "2026-11-03")))
                .andExpect(jsonPath("$.content[0].content").value("납기 일정 메일 회신"));
        mvc.perform(get("/api/suppliers/{id}/notes", otherSupplierId)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** 메모는 작성자 본인과 관리자만 지울 수 있다. 다른 담당자는 403, 지운 메모는 목록에서 빠진다. */
    @Test
    void onlyAuthorOrAdminDeletesNote() throws Exception {
        long mine = idOf(addNote("2026-11-17", "내가 쓴 메모"));
        long forAdmin = idOf(addNote("2026-11-16", "관리자가 지울 메모"));

        mvc.perform(delete("/api/suppliers/{id}/notes/{n}", supplierId, mine).with(user("manager2").roles("MANAGER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/suppliers/{id}/notes", supplierId).with(user("manager2").roles("MANAGER")))
                .andExpect(jsonPath("$.content[0].deletable").value(false));
        mvc.perform(delete("/api/suppliers/{id}/notes/{n}", otherSupplierId, mine)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/suppliers/{id}/notes/{n}", supplierId, forAdmin).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/suppliers/{id}/notes/{n}", supplierId, mine)).andExpect(status().isNoContent());

        mvc.perform(get("/api/suppliers/{id}/notes", supplierId)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** 메모는 작성자 본인과 관리자만 고칠 수 있다. 다른 담당자는 403, 오늘 이후 날짜는 막는다. (#82) */
    @Test
    void onlyAuthorOrAdminUpdatesNote() throws Exception {
        long id = idOf(addNote("2026-11-10", "오타가 있는 매모"));
        String fixed = "{\"contactDate\":\"2026-11-11\",\"content\":\"오타를 고친 메모\"}";

        mvc.perform(put("/api/suppliers/{id}/notes/{n}", supplierId, id).contentType(MediaType.APPLICATION_JSON).content(fixed)
                .with(user("manager2").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/suppliers/{id}/notes/{n}", supplierId, id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactDate\":\"2026-11-18\",\"content\":\"내일\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/suppliers/{id}/notes/{n}", supplierId, id).contentType(MediaType.APPLICATION_JSON).content(fixed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("오타를 고친 메모"))
                .andExpect(jsonPath("$.contactDate").value("2026-11-11")).andExpect(jsonPath("$.author").value("manager1"));
        mvc.perform(put("/api/suppliers/{id}/notes/{n}", supplierId, id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactDate\":\"2026-11-12\",\"content\":\"관리자가 고침\"}").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.author").value("manager1"));
        mvc.perform(get("/api/suppliers/{id}/notes", supplierId)).andExpect(jsonPath("$.content[0].content").value("관리자가 고침"));
    }

    /** 조회자는 담당자와 메모를 볼 수만 있다. */
    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerCanOnlyRead() throws Exception {
        mvc.perform(get("/api/suppliers/{id}/contacts", supplierId)).andExpect(status().isOk());
        mvc.perform(get("/api/suppliers/{id}/notes", supplierId)).andExpect(status().isOk());
        addContact("{\"name\":\"홍길동\"}").andExpect(status().isForbidden());
        addNote("2026-11-17", "메모").andExpect(status().isForbidden());
    }
}
