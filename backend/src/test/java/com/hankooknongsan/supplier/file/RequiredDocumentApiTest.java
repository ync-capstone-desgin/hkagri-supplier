package com.hankooknongsan.supplier.file;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.hankooknongsan.supplier.TestcontainersConfig;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * 필수 서류 점검 API 통합 테스트 (#47).
 * 관리자가 자료 종류를 필수로 정하면 업체별 제출·누락이 계산되는지, 계약 첨부도 제출로 세는지,
 * 협력업체 목록에서 누락 업체만 걸러지는지, 파일을 지우면 다시 누락이 되는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class RequiredDocumentApiTest {

    private static final byte[] PDF = "%PDF-1.7\n필수 서류 테스트\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired JdbcTemplate jdbc;

    String tag;
    Long supplierId;
    long docA;
    long docB;

    /** 테스트마다 새 협력업체와 필수 서류 종류 두 개를 만든다. */
    @BeforeEach
    void setUp() throws Exception {
        tag = UUID.randomUUID().toString().substring(0, 8);
        supplierId = suppliers.save(new Supplier("필수서류업체-" + tag, SupplierStatus.ACTIVE)).getId();
        docA = requiredCategory("필수A " + tag);
        docB = requiredCategory("필수B " + tag);
    }

    /** 이 테스트가 만든 필수 종류가 다른 테스트의 누락 건수에 섞이지 않게 필수 표시를 푼다. */
    @AfterEach
    void tearDown() {
        jdbc.update("update file_category set required = false where id in (?, ?)", docA, docB);
    }

    /** 관리자로 자료 종류를 만들고 필수로 정한 뒤 ID를 돌려준다. */
    long requiredCategory(String name) throws Exception {
        long id = mapper.readTree(mvc.perform(post("/api/admin/file-categories").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(patch("/api/admin/file-categories/" + id + "/required").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"required\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.required").value(true));
        return id;
    }

    /** 종류를 붙여 파일을 올리고 파일 ID를 돌려준다. contractId가 있으면 그 계약에 붙인다. */
    long upload(long categoryId, Long contractId) throws Exception {
        var request = multipart("/api/suppliers/{id}/files", supplierId)
                .file(new MockMultipartFile("file", "서류.pdf", "application/pdf", PDF)).param("categoryId", String.valueOf(categoryId));
        if (contractId != null) request.param("contractId", contractId.toString());
        return mapper.readTree(mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("file").path("id").asLong();
    }

    /** 이 테스트의 두 필수 서류에 대한 제출 여부만 골라 [A, B] 순서로 돌려준다. (다른 테스트의 필수 종류는 무시) */
    List<Boolean> submitted() throws Exception {
        var rows = mapper.readTree(mvc.perform(get("/api/suppliers/{id}/required-documents", supplierId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Boolean a = null;
        Boolean b = null;
        for (var row : rows) {
            if (row.path("categoryId").asLong() == docA) a = row.path("submitted").asBoolean();
            if (row.path("categoryId").asLong() == docB) b = row.path("submitted").asBoolean();
        }
        return List.of(a, b);
    }

    /** 협력업체 상세의 누락 서류 수 */
    long missingCount() throws Exception {
        return mapper.readTree(mvc.perform(get("/api/suppliers/" + supplierId)).andReturn().getResponse().getContentAsString())
                .path("missingDocCount").asLong();
    }

    /** 누락 업체만 검색했을 때 이 업체가 나오는지 */
    boolean listedAsMissing() throws Exception {
        var content = mapper.readTree(mvc.perform(get("/api/suppliers").param("keyword", "필수서류업체-" + tag).param("missingDocs", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("content");
        return content.size() == 1;
    }

    /** 필수 서류를 올리면 누락에서 빠지고, 모두 올리면 누락 업체 목록에서 사라진다. 파일을 지우면 다시 누락이 된다. */
    @Test
    void submittedAndMissingFollowUploadsAndDeletes() throws Exception {
        org.assertj.core.api.Assertions.assertThat(submitted()).containsExactly(false, false);
        long before = missingCount();
        org.assertj.core.api.Assertions.assertThat(listedAsMissing()).isTrue();

        long fileA = upload(docA, null);
        org.assertj.core.api.Assertions.assertThat(submitted()).containsExactly(true, false);
        org.assertj.core.api.Assertions.assertThat(missingCount()).isEqualTo(before - 1);

        mvc.perform(delete("/api/files/" + fileA)).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(submitted()).containsExactly(false, false);
        org.assertj.core.api.Assertions.assertThat(missingCount()).isEqualTo(before);
    }

    /** 계약에 붙인 파일도 그 업체의 제출 서류로 센다. 필수 표시를 풀면 점검 대상에서 빠진다. */
    @Test
    void contractAttachmentCountsAndRequiredFlagCanBeCleared() throws Exception {
        long contractId = mapper.readTree(mvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"supplierId":%d,"contractNumber":"R-1","title":"필수 서류 계약","startDate":"2026-01-01","endDate":"2026-12-31"}"""
                        .formatted(supplierId))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("item").path("id").asLong();
        upload(docA, contractId);
        org.assertj.core.api.Assertions.assertThat(submitted()).containsExactly(true, false);
        long before = missingCount();

        mvc.perform(patch("/api/admin/file-categories/" + docB + "/required").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"required\":false}")).andExpect(status().isOk());

        mvc.perform(get("/api/suppliers/{id}/required-documents", supplierId))
                .andExpect(jsonPath("$[?(@.categoryId == %d)]".formatted(docB), hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(missingCount()).isEqualTo(before - 1);
    }

    /** 해당 없음 요청을 보낸다. */
    org.springframework.test.web.servlet.ResultActions exempt(long supplier, long categoryId, boolean exempt) throws Exception {
        return mvc.perform(put("/api/suppliers/{s}/required-documents/{c}/exempt", supplier, categoryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"exempt\":" + exempt + "}"));
    }

    /** 해당 없음으로 뺀 서류는 그 업체의 누락에서만 빠지고, 되돌리면 다시 누락이 된다. 다른 업체에는 영향이 없다. (#85) */
    @Test
    void exemptDocumentIsNotCountedAsMissingForThatSupplierOnly() throws Exception {
        long other = suppliers.save(new Supplier("필수서류업체-다른-" + tag, SupplierStatus.ACTIVE)).getId();
        upload(docA, null);
        long before = missingCount();
        org.assertj.core.api.Assertions.assertThat(listedAsMissing()).isTrue();

        exempt(supplierId, docB, true).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId == %d)].exempt".formatted(docB)).value(true));
        exempt(supplierId, docB, true).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(missingCount()).isEqualTo(before - 1);
        org.assertj.core.api.Assertions.assertThat(listedAsMissing()).isFalse();
        mvc.perform(get("/api/suppliers/{id}/required-documents", other))
                .andExpect(jsonPath("$[?(@.categoryId == %d)].exempt".formatted(docB)).value(false));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select created_by from doc_exemption where supplier_id = ? and category_id = ?", String.class, supplierId, docB)).isEqualTo("manager1");

        exempt(supplierId, docB, false).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId == %d)].exempt".formatted(docB)).value(false));
        org.assertj.core.api.Assertions.assertThat(missingCount()).isEqualTo(before);
        exempt(999_999_999L, docB, true).andExpect(status().isNotFound());
        mvc.perform(put("/api/suppliers/{s}/required-documents/{c}/exempt", supplierId, docB).with(user("viewer").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"exempt\":true}")).andExpect(status().isForbidden());
    }

    /** 필수 여부는 관리자만 바꾸고, 조회자도 업체의 필수 서류 점검 결과는 볼 수 있다. */
    @Test
    void onlyAdminChangesRequiredAndViewerCanRead() throws Exception {
        mvc.perform(patch("/api/admin/file-categories/" + docA + "/required").contentType(MediaType.APPLICATION_JSON)
                .content("{\"required\":false}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/suppliers/{id}/required-documents", supplierId).with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/file-categories")).andExpect(jsonPath("$[?(@.id == %d)].required".formatted(docA)).value(true));
    }
}
