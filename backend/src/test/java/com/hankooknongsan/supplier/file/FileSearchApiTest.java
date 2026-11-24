package com.hankooknongsan.supplier.file;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 전체 자료 검색 API 통합 테스트 (#53).
 * 업체 자료·계약 첨부·인증 첨부·양식 자료실 파일을 파일 이름, 업체명, 계약명, 양식 제목으로 찾는지,
 * 삭제된 파일은 빠지는지, 조회자도 검색할 수 있는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class FileSearchApiTest {

    private static final byte[] PDF = "%PDF-1.7\n검색 테스트\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;

    /** 테스트마다 다른 값이 되도록 붙이는 꼬리표. 다른 테스트의 파일과 섞이지 않게 한다. */
    String tag;
    Long supplierId;

    /** 테스트마다 새 협력업체를 만든다. 업체명에도 꼬리표를 넣어 업체명 검색을 확인한다. */
    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        supplierId = suppliers.save(new Supplier("검색업체-" + tag, SupplierStatus.ACTIVE)).getId();
    }

    /** 업체 자료 탭으로 파일을 올리고 파일 ID를 돌려준다. param이 있으면 계약·인증에 연결한다. */
    long upload(String fileName, String param, Long targetId) throws Exception {
        var request = multipart("/api/suppliers/{id}/files", supplierId)
                .file(new MockMultipartFile("file", fileName, "application/pdf", PDF));
        if (param != null) request.param(param, targetId.toString());
        return mapper.readTree(mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("file").path("id").asLong();
    }

    /** 등록 응답에서 항목 ID를 꺼낸다. */
    long itemId(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("item").path("id").asLong();
    }

    /** 검색 API를 부른다. */
    ResultActions search(String keyword) throws Exception {
        return mvc.perform(get("/api/files/search").param("keyword", keyword)).andExpect(status().isOk());
    }

    /** 파일 이름의 일부(대소문자 무시)로 업체 일반 자료를 찾고, 어느 업체의 자료인지 함께 준다. */
    @Test
    void findsSupplierFileByPartOfName() throws Exception {
        long id = upload("사업자등록증-" + tag + "-Scan.pdf", null, null);

        search(tag.toUpperCase() + "-scan").andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].file.id").value(id))
                .andExpect(jsonPath("$.content[0].location").value("업체 자료"))
                .andExpect(jsonPath("$.content[0].supplierId").value(supplierId))
                .andExpect(jsonPath("$.content[0].supplierName").value("검색업체-" + tag));
    }

    /** 파일 이름에 단서가 없어도 계약명·인증 종류·업체명으로 첨부를 찾는다. */
    @Test
    void findsAttachmentsByContractTitleCertificationAndSupplierName() throws Exception {
        long contractId = itemId(mvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON).content("""
                {"supplierId":%d,"contractNumber":"S-1","title":"딸기 원료 %s 계약","startDate":"2026-01-01","endDate":"2026-12-31"}"""
                .formatted(supplierId, tag))));
        long certificationId = itemId(mvc.perform(post("/api/certifications").contentType(MediaType.APPLICATION_JSON).content("""
                {"supplierId":%d,"certTypeCode":"HACCP","certificateNumber":"CERT-%s","issuedDate":"2026-01-01","expiryDate":"2026-12-31"}"""
                .formatted(supplierId, tag))));
        long contractFile = upload("scan001.pdf", "contractId", contractId);
        long certificationFile = upload("scan002.pdf", "certificationId", certificationId);

        search("딸기 원료 " + tag).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].file.id").value(contractFile))
                .andExpect(jsonPath("$.content[0].location").value("계약 첨부"))
                .andExpect(jsonPath("$.content[0].supplierId").value(supplierId));
        search("CERT-" + tag).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].file.id").value(certificationFile))
                .andExpect(jsonPath("$.content[0].location").value("인증 첨부"));
        search("검색업체-" + tag).andExpect(jsonPath("$.content", hasSize(2)));
    }

    /** 양식 자료실 파일도 파일 이름과 양식 제목으로 찾고, 게시글과 폴더를 함께 준다. */
    @Test
    void findsFormFilesByNameAndTitle() throws Exception {
        var postPart = new MockMultipartFile("post", "", "application/json", """
                {"title":"온도 점검표 %s","category":"CHECKLIST","removeFileIds":[]}""".formatted(tag).getBytes(StandardCharsets.UTF_8));
        long formId = mapper.readTree(mvc.perform(multipart("/api/forms").file(postPart)
                        .file(new MockMultipartFile("files", "양식원본.pdf", "application/pdf", PDF)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();

        search("온도 점검표 " + tag).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].location").value("양식 자료실"))
                .andExpect(jsonPath("$.content[0].formId").value(formId))
                .andExpect(jsonPath("$.content[0].formTitle").value("온도 점검표 " + tag))
                .andExpect(jsonPath("$.content[0].folderId").value(1))
                .andExpect(jsonPath("$.content[0].supplierId").doesNotExist());

        mvc.perform(delete("/api/forms/" + formId)).andExpect(status().isNoContent());
        search("온도 점검표 " + tag).andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** 삭제한 파일은 검색에서 빠진다. */
    @Test
    void deletedFilesAreExcluded() throws Exception {
        long id = upload("삭제될자료-" + tag + ".pdf", null, null);
        mvc.perform(delete("/api/files/" + id)).andExpect(status().isNoContent());

        search("삭제될자료-" + tag).andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** 조회자도 검색하고 내려받을 수 있다. */
    @Test
    void viewerCanSearchAndDownload() throws Exception {
        long id = upload("조회자확인-" + tag + ".pdf", null, null);

        mvc.perform(get("/api/files/search").param("keyword", "조회자확인-" + tag)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/files/" + id + "/download")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER")))
                .andExpect(status().isOk());
    }
}
