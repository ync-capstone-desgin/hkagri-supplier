package com.hankooknongsan.supplier.certification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 필수 인증 만료 표시 API 통합 테스트 (#46).
 * 관리자가 필수로 정한 인증 종류의 현재 인증이 만료된 업체만 표시되는지, 갱신하면 사라지는지,
 * 필수가 아닌 종류나 인증이 없는 업체는 표시되지 않는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class RequiredCertExpiredApiTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired AppClock clock;
    @Autowired JdbcTemplate jdbc;

    String tag;
    Long supplierId;
    String requiredType;
    String optionalType;

    /** 테스트마다 기준일을 고정하고, 새 업체와 필수·선택 인증 종류를 하나씩 만든다. */
    @BeforeEach
    void setUp() throws Exception {
        clock.setToday(TODAY);
        tag = UUID.randomUUID().toString().substring(0, 8);
        supplierId = suppliers.save(new Supplier("인증만료업체-" + tag, SupplierStatus.ACTIVE)).getId();
        requiredType = certType("필수인증 " + tag);
        optionalType = certType("선택인증 " + tag);
        mvc.perform(patch("/api/admin/cert-types/" + requiredType + "/required").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"required\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.required").value(true));
    }

    /** 기준일을 되돌리고, 이 테스트가 만든 필수 표시가 다른 테스트에 섞이지 않게 푼다. */
    @AfterEach
    void tearDown() {
        clock.setToday(null);
        jdbc.update("update cert_type set required = false where code = ?", requiredType);
    }

    /** 관리자로 인증 종류를 만들고 코드를 돌려준다. */
    String certType(String name) throws Exception {
        return json(mvc.perform(post("/api/admin/cert-types").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("code").asText();
    }

    /** 인증을 등록(url이 갱신 주소면 갱신)하고 새 인증 ID를 돌려준다. */
    long certification(String url, String typeCode, String expiryDate) throws Exception {
        return json(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("""
                {"supplierId":%d,"certTypeCode":"%s","certificateNumber":"E-%s","issuedDate":"2025-01-01","expiryDate":"%s"}"""
                .formatted(supplierId, typeCode, UUID.randomUUID(), expiryDate)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("item").path("id").asLong();
    }

    JsonNode json(String body) {
        return mapper.readTree(body);
    }

    /** 협력업체 상세의 만료된 필수 인증 수 */
    long expiredCount() throws Exception {
        return json(mvc.perform(get("/api/suppliers/" + supplierId)).andReturn().getResponse().getContentAsString())
                .path("expiredRequiredCertCount").asLong();
    }

    /** "필수 인증 만료만" 조건으로 검색했을 때 이 업체가 나오는지 */
    boolean listedAsExpired() throws Exception {
        return json(mvc.perform(get("/api/suppliers").param("keyword", "인증만료업체-" + tag).param("certExpired", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("content").size() == 1;
    }

    /** 대시보드에 쓰는 필수 인증 만료 업체 수 */
    long dashboardCount() {
        return suppliers.countCertExpired(TODAY);
    }

    /** 필수 인증이 어제 만료됐으면 표시되고, 갱신하면 사라진다. 오늘 만료되는 건은 아직 만료가 아니다. */
    @Test
    void expiredRequiredCertificationIsFlaggedUntilRenewed() throws Exception {
        assertThat(expiredCount()).isZero();
        assertThat(listedAsExpired()).isFalse();
        long before = dashboardCount();

        long today = certification("/api/certifications", requiredType, "2026-11-17");
        assertThat(expiredCount()).isZero();

        long old = certification("/api/certifications/" + today + "/renew", requiredType, "2026-11-16");
        assertThat(expiredCount()).isEqualTo(1);
        assertThat(listedAsExpired()).isTrue();
        assertThat(dashboardCount()).isEqualTo(before + 1);

        certification("/api/certifications/" + old + "/renew", requiredType, "2027-11-16");
        assertThat(expiredCount()).isZero();
        assertThat(listedAsExpired()).isFalse();
        assertThat(dashboardCount()).isEqualTo(before);
    }

    /** 필수가 아닌 종류의 인증이 만료된 업체는 표시하지 않는다. 그 종류를 필수로 바꾸면 표시된다. */
    @Test
    void onlyRequiredTypesCount() throws Exception {
        certification("/api/certifications", optionalType, "2026-01-01");
        assertThat(expiredCount()).isZero();
        assertThat(listedAsExpired()).isFalse();

        mvc.perform(patch("/api/admin/cert-types/" + optionalType + "/required").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"required\":true}")).andExpect(status().isOk());
        try {
            assertThat(expiredCount()).isEqualTo(1);
            assertThat(listedAsExpired()).isTrue();
        } finally {
            jdbc.update("update cert_type set required = false where code = ?", optionalType);
        }
    }

    /** 필수 여부는 관리자만 바꾸고, 인증 종류 목록에 필수 여부가 함께 나온다. */
    @Test
    void onlyAdminChangesRequired() throws Exception {
        mvc.perform(patch("/api/admin/cert-types/" + requiredType + "/required").contentType(MediaType.APPLICATION_JSON)
                .content("{\"required\":false}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/cert-types")).andExpect(jsonPath("$[?(@.code == '%s')].required".formatted(requiredType)).value(true))
                .andExpect(jsonPath("$[?(@.code == '%s')].required".formatted(optionalType)).value(false));
    }
}
