package com.hankooknongsan.supplier.supplier;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    SupplierRepository suppliers;

    /** VT-001: 정상 등록하면 업체코드가 발급되고 상세 조회 결과가 입력값과 같다. */
    @Test
    @WithMockUser(username = "manager1", roles = "MANAGER")
    void 정상_등록하면_목록과_상세에_같은_정보가_보인다() throws Exception {
        String body = mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"테스트농산","businessNumber":"123-45-67891","status":"ACTIVE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(startsWith("SUP-")))
                .andExpect(jsonPath("$.createdBy").value("manager1"))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/suppliers/" + id))
                .andExpect(jsonPath("$.name").value("테스트농산"))
                .andExpect(jsonPath("$.businessNumber").value("123-45-67891"));
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

    /** 검증번호가 틀린 사업자번호는 저장하지 않고 사업자번호 칸 오류를 알려준다. */
    @Test
    @WithMockUser(roles = "MANAGER")
    void 검증번호가_틀린_사업자번호는_저장하지_않는다() throws Exception {
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"오타농산","businessNumber":"124-81-00999","status":"ACTIVE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("businessNumber"));
    }

    /** 예전에 저장된 번호가 틀려도, 번호를 그대로 두고 다른 칸만 고치는 수정은 막지 않는다. */
    @Test
    @WithMockUser(roles = "MANAGER")
    void 기존_번호를_그대로_두면_다른_칸_수정은_된다() throws Exception {
        Supplier old = new Supplier("옛데이터농산", SupplierStatus.ACTIVE);
        old.update("옛데이터농산", "100-10-10000", SupplierStatus.ACTIVE, null, null, null, null, null);
        Long id = suppliers.save(old).getId();

        mvc.perform(put("/api/suppliers/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"옛데이터농산(수정)","businessNumber":"100-10-10000","status":"ACTIVE"}"""))
                .andExpect(status().isOk());
        mvc.perform(put("/api/suppliers/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"옛데이터농산","businessNumber":"100-10-10002","status":"ACTIVE"}"""))
                .andExpect(status().isBadRequest());
    }

    /** 사업자번호 확인: 같은 번호로 등록된 다른 업체를 알려주고, 수정 중인 자기 자신은 빼고 보여준다. */
    @Test
    @WithMockUser(roles = "MANAGER")
    void 같은_사업자번호로_등록된_업체를_알려준다() throws Exception {
        String number = BusinessNumber.withCheckDigit("%09d".formatted(System.nanoTime() % 1_000_000_000L));
        Supplier first = new Supplier("중복원본", SupplierStatus.ACTIVE);
        first.update("중복원본", number, SupplierStatus.ACTIVE, null, null, null, null, null);
        Long firstId = suppliers.save(first).getId();

        mvc.perform(get("/api/suppliers/business-number-check").param("number", number))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.duplicates[*].name").value(contains("중복원본")));
        mvc.perform(get("/api/suppliers/business-number-check").param("number", number).param("exceptId", firstId.toString()))
                .andExpect(jsonPath("$.duplicates", empty()));
        mvc.perform(get("/api/suppliers/business-number-check").param("number", "124-81-00999"))
                .andExpect(jsonPath("$.valid").value(false));
    }
}
