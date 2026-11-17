package com.hankooknongsan.supplier.history;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.hankooknongsan.supplier.TestcontainersConfig;
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
 * 변경 이력 API 통합 테스트 (#45).
 * 협력업체·계약·인증의 등록·수정·갱신·처리상태 변경·삭제가 항목별 변경 내용과 함께 남는지,
 * 업체 상세 이력은 모든 사용자가, 전체 이력은 관리자만 보는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class ChangeLogApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    long supplierId;
    String supplierName;

    /** 테스트마다 담당자 권한으로 새 협력업체를 등록한다. (등록 이력이 한 건 남는다. 조회자 테스트에서도 준비는 담당자로 한다) */
    @BeforeEach
    void setUp() throws Exception {
        supplierName = "이력 테스트 " + UUID.randomUUID();
        supplierId = mapper.readTree(mvc.perform(post("/api/suppliers").with(user("manager1").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(supplierJson("010-1111-2222")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    /** 협력업체 등록·수정 요청 본문 */
    String supplierJson(String phone) {
        return """
                {"name":"%s","status":"ACTIVE","contactPhone":"%s"}""".formatted(supplierName, phone);
    }

    /** 계약 등록·수정·갱신 요청 본문 */
    String contractJson(String title, String endDate) {
        return """
                {"supplierId":%d,"contractNumber":"H-1","title":"%s","startDate":"2026-01-01","endDate":"%s"}"""
                .formatted(supplierId, title, endDate);
    }

    /** 등록·갱신 응답에서 항목 ID를 꺼낸다. */
    long itemId(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("item").path("id").asLong();
    }

    /** 이 업체의 변경 이력을 최신순으로 조회한다. */
    ResultActions changes() throws Exception {
        return mvc.perform(get("/api/suppliers/{id}/changes", supplierId)).andExpect(status().isOk());
    }

    /** 협력업체를 수정하면 달라진 항목만 이전 값·바뀐 값과 함께 남고, 달라진 것이 없으면 남지 않는다. */
    @Test
    void supplierUpdateRecordsOnlyChangedFields() throws Exception {
        mvc.perform(put("/api/suppliers/" + supplierId).contentType(MediaType.APPLICATION_JSON)
                .content(supplierJson("010-9999-0000"))).andExpect(status().isOk());
        mvc.perform(put("/api/suppliers/" + supplierId).contentType(MediaType.APPLICATION_JSON)
                .content(supplierJson("010-9999-0000"))).andExpect(status().isOk());

        changes().andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.content[0].targetLabel").value("협력업체"))
                .andExpect(jsonPath("$.content[0].actor").value("manager1"))
                .andExpect(jsonPath("$.content[0].changes", hasSize(1)))
                .andExpect(jsonPath("$.content[0].changes[0].label").value("연락처"))
                .andExpect(jsonPath("$.content[0].changes[0].before").value("010-1111-2222"))
                .andExpect(jsonPath("$.content[0].changes[0].after").value("010-9999-0000"))
                .andExpect(jsonPath("$.content[1].action").value("CREATE"))
                .andExpect(jsonPath("$.content[1].changes[?(@.label == '업체명')].after").value(supplierName));
    }

    /** 계약의 등록·수정·처리상태 변경·갱신·삭제가 모두 업체 이력에 순서대로 남고, 삭제한 뒤에도 조회된다. */
    @Test
    void contractLifecycleIsRecordedUnderSupplier() throws Exception {
        long id = itemId(mvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON)
                .content(contractJson("원료 공급 계약", "2026-12-31"))));
        mvc.perform(put("/api/contracts/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(contractJson("원료 공급 계약", "2027-06-30"))).andExpect(status().isOk());
        mvc.perform(patch("/api/contracts/" + id + "/renewal-status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"renewalStatus\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
        long renewedId = itemId(mvc.perform(post("/api/contracts/" + id + "/renew").contentType(MediaType.APPLICATION_JSON)
                .content(contractJson("원료 공급 계약", "2028-06-30"))));
        mvc.perform(delete("/api/contracts/" + renewedId)).andExpect(status().isNoContent());

        changes().andExpect(jsonPath("$.content", hasSize(6)))
                .andExpect(jsonPath("$.content[0].action").value("DELETE"))
                .andExpect(jsonPath("$.content[0].targetId").value(renewedId))
                .andExpect(jsonPath("$.content[0].targetName").value("원료 공급 계약"))
                .andExpect(jsonPath("$.content[1].action").value("RENEW"))
                .andExpect(jsonPath("$.content[1].changes[?(@.label == '종료일')].before").value("2027-06-30"))
                .andExpect(jsonPath("$.content[1].changes[?(@.label == '종료일')].after").value("2028-06-30"))
                .andExpect(jsonPath("$.content[2].action").value("STATUS"))
                .andExpect(jsonPath("$.content[2].changes[0].before").value("미착수"))
                .andExpect(jsonPath("$.content[2].changes[0].after").value("갱신진행"))
                .andExpect(jsonPath("$.content[3].action").value("UPDATE"))
                .andExpect(jsonPath("$.content[3].changes", hasSize(1)))
                .andExpect(jsonPath("$.content[3].changes[0].label").value("종료일"))
                .andExpect(jsonPath("$.content[4].action").value("CREATE"))
                .andExpect(jsonPath("$.content[4].targetLabel").value("계약"))
                .andExpect(jsonPath("$.content[5].targetLabel").value("협력업체"));
    }

    /** 인증 등록·삭제도 업체 이력에 남고, 대상 이름에 인증 종류가 보인다. */
    @Test
    void certificationIsRecorded() throws Exception {
        long id = itemId(mvc.perform(post("/api/certifications").contentType(MediaType.APPLICATION_JSON).content("""
                {"supplierId":%d,"certTypeCode":"HACCP","certificateNumber":"H-%s","scope":"잼류","issuedDate":"2026-01-01","expiryDate":"2026-12-31"}"""
                .formatted(supplierId, UUID.randomUUID()))));
        mvc.perform(delete("/api/certifications/" + id)).andExpect(status().isNoContent());

        changes().andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].action").value("DELETE"))
                .andExpect(jsonPath("$.content[0].targetLabel").value("인증"))
                .andExpect(jsonPath("$.content[1].action").value("CREATE"))
                .andExpect(jsonPath("$.content[1].changes[?(@.label == '적용 대상')].after").value("잼류"));
    }

    /** 업체를 삭제해도 그 업체의 이력은 전체 이력에서 찾을 수 있다. 전체 이력은 관리자만 본다. */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminSearchesAllChangesIncludingDeletedSupplier() throws Exception {
        mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/changes").param("keyword", supplierName).param("targetType", "SUPPLIER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action").value("DELETE"))
                .andExpect(jsonPath("$.content[0].actor").value("admin"));
        mvc.perform(get("/api/admin/changes").param("keyword", supplierName).param("targetType", "CONTRACT"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** 조회자는 업체 이력은 보지만 전체 이력은 볼 수 없다. */
    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerSeesSupplierChangesButNotAll() throws Exception {
        mvc.perform(get("/api/suppliers/{id}/changes", supplierId)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/changes")).andExpect(status().isForbidden());
    }
}
