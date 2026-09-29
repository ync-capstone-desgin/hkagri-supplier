package com.hankooknongsan.supplier.supplier;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankooknongsan.supplier.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 협력업체 API 통합 테스트.
 * 검증 시나리오 VT-001(정상 등록), VT-002(필수값 누락)와 역할별 권한을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SupplierApiTest {

    @Autowired
    MockMvc mvc;

    /** VT-001: 정상 등록하면 업체코드가 발급되고 상세 조회 결과가 입력값과 같다. */
    @Test
    @WithMockUser(username = "manager1", roles = "MANAGER")
    void 정상_등록하면_목록과_상세에_같은_정보가_보인다() throws Exception {
        String body = mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"테스트농산","businessNumber":"123-45-67890","status":"ACTIVE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(startsWith("SUP-")))
                .andExpect(jsonPath("$.createdBy").value("manager1"))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/suppliers/" + id))
                .andExpect(jsonPath("$.name").value("테스트농산"))
                .andExpect(jsonPath("$.businessNumber").value("123-45-67890"));
        mvc.perform(get("/api/suppliers").param("keyword", "테스트농산"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** VT-002: 필수값이 없거나 형식이 틀리면 저장하지 않고 칸별 오류를 알려준다. */
    @Test
    @WithMockUser(roles = "MANAGER")
    void 필수값_누락이면_400과_칸별_오류를_돌려준다() throws Exception {
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"","businessNumber":"12345"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("name", "status", "businessNumber")));
    }

    /** 조회자(VIEWER)는 목록을 볼 수 있지만 등록은 할 수 없다. */
    @Test
    @WithMockUser(roles = "VIEWER")
    void 조회자는_등록할_수_없다() throws Exception {
        mvc.perform(get("/api/suppliers")).andExpect(status().isOk());
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"x","status":"ACTIVE"}"""))
                .andExpect(status().isForbidden());
    }

    /** 로그인하지 않으면 401을 돌려준다. */
    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/suppliers")).andExpect(status().isUnauthorized());
    }
}
