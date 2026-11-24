package com.hankooknongsan.supplier.file;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * 자료 종류 API 통합 테스트 (#54).
 * 관리자만 종류를 추가·수정·사용 중지하는지, 파일을 올릴 때와 올린 뒤에 종류를 붙이고 바꿀 수 있는지,
 * 업체 자료 탭과 자료 검색에서 종류로 걸러지는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class FileCategoryApiTest {

    private static final byte[] PDF = "%PDF-1.7\n종류 테스트\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;

    String tag;
    Long supplierId;

    /** 테스트마다 새 협력업체와, 다른 테스트와 겹치지 않는 꼬리표를 만든다. */
    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        supplierId = suppliers.save(new Supplier("종류업체-" + tag, SupplierStatus.ACTIVE)).getId();
    }

    /** 관리자로 새 자료 종류를 추가하고 ID를 돌려준다. */
    long category(String name) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/admin/file-categories").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    /** 업체 자료 탭으로 파일을 올리고 파일 ID를 돌려준다. categoryId가 null이면 종류 없이 올린다. */
    long upload(String fileName, Long categoryId) throws Exception {
        var request = multipart("/api/suppliers/{id}/files", supplierId)
                .file(new MockMultipartFile("file", fileName, "application/pdf", PDF));
        if (categoryId != null) request.param("categoryId", categoryId.toString());
        return mapper.readTree(mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("file").path("id").asLong();
    }

    /** 처음 넣어 둔 종류가 있고, 조회자도 목록을 볼 수 있다. */
    @Test
    void seededCategoriesAreListedForEveryone() throws Exception {
        mvc.perform(get("/api/file-categories").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(7))))
                .andExpect(jsonPath("$[*].name", hasItems("사업자등록증", "통장 사본", "계약서", "인증서", "표기사항", "품목제조보고서", "성적서")));
    }

    /** 관리자만 종류를 추가·이름 수정·사용 중지할 수 있고, 공백·대소문자만 다른 같은 이름은 막는다. */
    @Test
    void onlyAdminManagesCategoriesAndDuplicateNamesAreRejected() throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("name", "거래명세서 " + tag));
        mvc.perform(post("/api/admin/file-categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        long id = category("거래명세서 " + tag);

        mvc.perform(post("/api/admin/file-categories").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", "거래 명세서" + tag.toUpperCase()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("name"));
        mvc.perform(put("/api/admin/file-categories/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", "납품서 " + tag))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("납품서 " + tag));
        mvc.perform(patch("/api/admin/file-categories/" + id + "/active").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/file-categories")).andExpect(jsonPath("$[?(@.id == %d)].active".formatted(id)).value(false));
    }

    /** 올릴 때 종류를 붙이거나 비울 수 있고, 업체 자료 탭과 자료 검색에서 종류로 걸러 볼 수 있다. */
    @Test
    void uploadWithOrWithoutCategoryAndFilter() throws Exception {
        long categoryId = category("시험 종류 " + tag);
        long tagged = upload("종류있음-" + tag + ".pdf", categoryId);
        long plain = upload("종류없음-" + tag + ".pdf", null);

        mvc.perform(get("/api/suppliers/{id}/files", supplierId)).andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.file.id == %d)].categoryName".formatted(tagged)).value("시험 종류 " + tag))
                .andExpect(jsonPath("$.content[?(@.file.id == %d)].categoryId".formatted(plain)).value((Object) null));
        mvc.perform(get("/api/suppliers/{id}/files", supplierId).param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].file.id").value(tagged));
        mvc.perform(get("/api/files/search").param("keyword", tag).param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].file.id").value(tagged))
                .andExpect(jsonPath("$.content[0].categoryName").value("시험 종류 " + tag));
        mvc.perform(get("/api/files/search").param("keyword", tag)).andExpect(jsonPath("$.content", hasSize(2)));
    }

    /** 이미 올린 파일의 종류를 바꾸고 지울 수 있다. 사용 중지한 종류는 새로 붙일 수 없지만 이미 붙인 것은 남는다. 조회자는 바꿀 수 없다. */
    @Test
    void changeCategoryOfExistingFile() throws Exception {
        long first = category("처음 종류 " + tag);
        long second = category("바꿀 종류 " + tag);
        long fileId = upload("종류변경-" + tag + ".pdf", first);

        mvc.perform(patch("/api/files/" + fileId + "/category").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + second + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryName").value("바꿀 종류 " + tag));
        mvc.perform(patch("/api/admin/file-categories/" + second + "/active").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/suppliers/{id}/files", supplierId))
                .andExpect(jsonPath("$.content[0].categoryName").value("바꿀 종류 " + tag));
        long other = upload("다른파일-" + tag + ".pdf", null);
        mvc.perform(patch("/api/files/" + other + "/category").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + second + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        mvc.perform(multipart("/api/suppliers/{id}/files", supplierId)
                        .file(new MockMultipartFile("file", "막힘.pdf", "application/pdf", PDF)).param("categoryId", String.valueOf(second)))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/files/" + fileId + "/category").contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value((Object) null));
        mvc.perform(patch("/api/files/" + fileId + "/category").with(user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":" + first + "}"))
                .andExpect(status().isForbidden());
    }
}
