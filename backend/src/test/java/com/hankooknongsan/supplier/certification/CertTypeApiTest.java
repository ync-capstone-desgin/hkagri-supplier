package com.hankooknongsan.supplier.certification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/** 실제 DB에서 종류 관리 권한·이름 충돌·기존 인증과 갱신 이력 보존을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(roles = "ADMIN")
class CertTypeApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;

    /** 새 종류는 공백을 정리하고 담당자가 인증에 사용할 수 있다는 동작을 검증한다. */
    @Test
    void 새_종류는_공백을_정리하고_담당자가_인증에_사용할_수_있다() throws Exception {
        String name = "테스트 " + UUID.randomUUID();
        String code = codeOf(createType("  " + name + "  ")
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.active").value(true)));
        mvc.perform(get("/api/cert-types").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].code", hasItem(code)));
        certificate(post("/api/certifications").with(user("manager").roles("MANAGER")), supplier(), code, "신규")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.item.certTypeName").value(name));
    }

    /** 종류 이름을 바꿔도 인증과 갱신연결과 문서링크를 보존한다는 동작을 검증한다. */
    @Test
    void 종류_이름을_바꿔도_인증과_갱신연결과_문서링크를_보존한다() throws Exception {
        String code = codeOf(createType("이력 " + UUID.randomUUID()));
        long supplierId = supplier();
        long oldId = idOf(certificate(post("/api/certifications"), supplierId, code, "이전"));
        long newId = idOf(certificate(post("/api/certifications/" + oldId + "/renew"), supplierId, code, "갱신"));
        String renamed = "변경 " + UUID.randomUUID();
        updateType(code, renamed, true).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
        mvc.perform(get("/api/certifications/" + oldId)).andExpect(jsonPath("$.certTypeName").value(renamed))
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.renewalStatus").value("DONE"))
                .andExpect(jsonPath("$.documentUrl").value("https://example.com/certificate.pdf"));
        mvc.perform(get("/api/certifications/" + newId)).andExpect(jsonPath("$.prevId").value(oldId))
                .andExpect(jsonPath("$.certTypeCode").value(code)).andExpect(jsonPath("$.certTypeName").value(renamed));
    }

    /** 공백과 대소문자만 다른 이름은 비활성이어도 중복이다는 동작을 검증한다. */
    @Test
    void 공백과_대소문자만_다른_이름은_비활성이어도_중복이다() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String code = codeOf(createType("Test " + suffix));
        updateType(code, "Test " + suffix, false).andExpect(status().isOk());
        createType(" test\t" + suffix + " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        String other = codeOf(createType("다른 " + suffix));
        updateType(other, "TEST" + suffix, true).andExpect(status().isBadRequest());
        updateType(code, "TEST " + suffix, true).andExpect(status().isOk());
    }

    /** 빈이름과 길이초과와 없는종류를 검증한다는 동작을 검증한다. */
    @Test
    void 빈이름과_길이초과와_없는종류를_검증한다() throws Exception {
        for (String name : new String[]{"", " \t\n", "\u3000", "가".repeat(101)}) {
            createType(name).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }
        updateType("missing", "없는 종류", true).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/cert-types/HACCP/active").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }

    /** 소문자 변환으로 길이가 늘어나는 이름도 저장한다는 동작을 검증한다. */
    @Test
    void 소문자_변환으로_길이가_늘어나는_이름도_저장한다() throws Exception {
        createType("İ".repeat(100)).andExpect(status().isCreated());
    }

    /** 이름수정과 사용상태변경은 서로 덮어쓰지 않는다는 동작을 검증한다. */
    @Test
    void 이름수정과_사용상태변경은_서로_덮어쓰지_않는다() throws Exception {
        String name = "분리 " + UUID.randomUUID();
        String code = codeOf(createType(name));
        updateType(code, name, false).andExpect(status().isOk());
        String renamed = name + " 수정";
        mvc.perform(put("/api/admin/cert-types/" + code).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", renamed))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/admin/cert-types/" + code + "/active").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(renamed));
    }

    /** 비관리자는 종류를 추가하거나 바꿀 수 없다는 동작을 검증한다. */
    @Test
    void 비관리자는_종류를_추가하거나_바꿀_수_없다() throws Exception {
        String name = "권한 " + UUID.randomUUID();
        String code = codeOf(createType(name));
        for (String role : new String[]{"MANAGER", "VIEWER"}) {
            mvc.perform(post("/api/admin/cert-types").with(user("other").roles(role))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"차단\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/cert-types/" + code).with(user("other").roles(role))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"차단\",\"active\":false}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/cert-types/" + code + "/active").with(user("other").roles(role))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/cert-types")).andExpect(jsonPath("$[?(@.code == '" + code + "')].name", hasItem(name)))
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].active", hasItem(true)));
    }

    /** 조회자는 인증도 등록하지 못한다는 동작을 검증한다. */
    @Test
    @WithMockUser(roles = "VIEWER")
    void 조회자는_인증도_등록하지_못한다() throws Exception {
        certificate(post("/api/certifications"), supplier(), "HACCP", "차단").andExpect(status().isForbidden());
    }

    /** 비활성 종류의 기존수정은 허용하고 신규와 갱신은 차단한다는 동작을 검증한다. */
    @Test
    void 비활성_종류의_기존수정은_허용하고_신규와_갱신은_차단한다() throws Exception {
        String name = "비활성 " + UUID.randomUUID();
        String code = codeOf(createType(name));
        long supplierId = supplier();
        long oldId = idOf(certificate(post("/api/certifications"), supplierId, code, "이전"));
        updateType(code, name, false).andExpect(status().isOk());
        certificate(put("/api/certifications/" + oldId), supplierId, code, "수정")
                .andExpect(status().isOk()).andExpect(jsonPath("$.item.certTypeCode").value(code));
        certificate(post("/api/certifications"), supplierId, code, "차단").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("certTypeCode"));
        certificate(post("/api/certifications/" + oldId + "/renew"), supplierId, code, "차단 갱신")
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/certifications/" + oldId)).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.renewalStatus").value("NONE"));
        mvc.perform(get("/api/certifications").param("supplierId", Long.toString(supplierId)).param("certType", code))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].certificateNumber").value("수정"));
        updateType(code, name, true).andExpect(status().isOk());
        certificate(post("/api/certifications/" + oldId + "/renew"), supplierId, code, "재활성 갱신")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.item.prevId").value(oldId));
    }

    /** 다른 비활성 종류로 변경할 수 없고 활성 종류로는 갱신할 수 있다는 동작을 검증한다. */
    @Test
    void 다른_비활성_종류로_변경할_수_없고_활성_종류로는_갱신할_수_있다() throws Exception {
        String name = "폐기 " + UUID.randomUUID();
        String inactive = codeOf(createType(name));
        String active = codeOf(createType("사용 " + UUID.randomUUID()));
        long supplierId = supplier();
        long inactiveId = idOf(certificate(post("/api/certifications"), supplierId, inactive, "이전"));
        long activeId = idOf(certificate(post("/api/certifications"), supplierId, active, "현재"));
        updateType(inactive, name, false).andExpect(status().isOk());
        certificate(put("/api/certifications/" + activeId), supplierId, inactive, "차단").andExpect(status().isBadRequest());
        certificate(post("/api/certifications/" + inactiveId + "/renew"), supplierId, active, "종류 변경 갱신")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.item.certTypeCode").value(active))
                .andExpect(jsonPath("$.item.prevId").value(inactiveId));
        mvc.perform(get("/api/certifications/" + inactiveId)).andExpect(jsonPath("$.certTypeCode").value(inactive));
    }

    /** 동시에 같은이름을 추가해도 한건만 생긴다는 동작을 검증한다. */
    @Test
    void 동시에_같은이름을_추가해도_한건만_생긴다() throws Exception {
        String name = "동시 " + UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> concurrentCreate(name, start));
            var second = executor.submit(() -> concurrentCreate(name.toUpperCase(), start));
            start.countDown();
            assertThat(new int[]{first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 400);
        }
        mvc.perform(get("/api/cert-types"))
                .andExpect(jsonPath("$[?(@.name == '" + name + "' || @.name == '" + name.toUpperCase() + "')].code", hasSize(1)));
    }

    /** 동시에 출발한 별도 관리자 요청의 응답 상태를 수집한다. */
    private int concurrentCreate(String name, CountDownLatch start) throws Exception {
        start.await();
        return mvc.perform(post("/api/admin/cert-types").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name", name))))
                .andReturn().getResponse().getStatus();
    }

    /** 인증 종류 추가 요청을 보낸다. */
    private ResultActions createType(String name) throws Exception {
        return mvc.perform(post("/api/admin/cert-types").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", name))));
    }

    /** 이름과 사용 여부를 각각의 API로 변경한다. */
    private ResultActions updateType(String code, String name, boolean active) throws Exception {
        ResultActions renamed = mvc.perform(put("/api/admin/cert-types/" + code).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", name))));
        if (renamed.andReturn().getResponse().getStatus() != 200) return renamed;
        return mvc.perform(patch("/api/admin/cert-types/" + code + "/active").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("active", active))));
    }

    /** 생성 응답에서 인증 종류 코드를 읽는다. */
    private String codeOf(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("code").asText();
    }

    /** 인증 생성 응답에서 식별자를 읽는다. */
    private long idOf(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("item").path("id").asLong();
    }

    /** 다른 테스트와 겹치지 않는 검증용 업체를 만든다. */
    private long supplier() {
        return suppliers.save(new Supplier("종류검증업체", SupplierStatus.ACTIVE)).getId();
    }

    /** 문서 링크와 메모를 가진 인증 요청을 보낸다. */
    private ResultActions certificate(MockHttpServletRequestBuilder request, long supplierId, String code, String number) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "supplierId", supplierId, "certTypeCode", code, "certificateNumber", number,
                "issuedDate", "2026-01-01", "expiryDate", "2027-12-31", "memo", "메모 수정",
                "documentUrl", "https://example.com/certificate.pdf"))));
    }
}
