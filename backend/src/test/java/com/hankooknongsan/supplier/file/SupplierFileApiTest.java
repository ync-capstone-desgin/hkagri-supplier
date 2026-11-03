package com.hankooknongsan.supplier.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.certification.Certification;
import com.hankooknongsan.supplier.certification.CertificationRepository;
import com.hankooknongsan.supplier.certification.CertTypeRepository;
import com.hankooknongsan.supplier.contract.Contract;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 업체 자료 탭의 데이터 격리·연결 검증·다운로드 원본·권한·삭제 이력을 실제 DB로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class SupplierFileApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired ContractRepository contracts;
    @Autowired CertificationRepository certifications;
    @Autowired CertTypeRepository certTypes;
    @Autowired StoredFileRepository files;
    @Autowired TransactionTemplate tx;
    Supplier a;
    Supplier b;
    Contract contract;
    Certification certification;
    private static final byte[] PDF = "%PDF-1.7\n업체 자료 테스트\n%%EOF".getBytes(StandardCharsets.UTF_8);

    /** 업체 두 곳과 첫 업체의 계약·인증을 만든다. */
    @BeforeEach
    void setUp() {
        a = suppliers.save(new Supplier("자료 업체 A", SupplierStatus.ACTIVE));
        b = suppliers.save(new Supplier("자료 업체 B", SupplierStatus.ACTIVE));
        contract = new Contract(a);
        contract.update(a, "FILE-CT", "자료 계약", LocalDate.now(), LocalDate.now().plusYears(1), false, null, null, null);
        contract = contracts.save(contract);
        certification = new Certification(a);
        certification.update(a, certTypes.findById("HACCP").orElseThrow(), "FILE-CERT", LocalDate.now(),
                LocalDate.now().plusYears(1), null, null, null);
        certification = certifications.save(certification);
    }

    /** 서명과 한글 내용이 있는 가상 PDF 업로드 요청을 만든다. */
    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "업체 자료.pdf", "application/pdf", PDF);
    }

    /** 업체 자료 업로드 응답에서 파일 ID를 꺼낸다. */
    private long idOf(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("file").path("id").asLong();
    }

    /** 업체별 목록에만 보이고 원본 다운로드·삭제 후 목록 제외·삭제 이력이 함께 유지된다. */
    @Test
    void 업체_격리와_원본_다운로드_삭제_이력을_보존한다() throws Exception {
        long id = idOf(mvc.perform(multipart("/api/suppliers/{id}/files", a.getId()).file(pdf())));
        mvc.perform(get("/api/suppliers/{id}/files", a.getId()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].file.createdBy").value("manager1"))
                .andExpect(jsonPath("$.content[0].file.size").value(PDF.length))
                .andExpect(jsonPath("$.content[0].file.createdAt").isNotEmpty());
        mvc.perform(get("/api/suppliers/{id}/files", b.getId())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/files/{id}/download", id).with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")));
        mvc.perform(delete("/api/files/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/suppliers/{id}/files", a.getId())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/files/{id}/download", id)).andExpect(status().isNotFound());
        var deleted = files.findById(id).orElseThrow();
        assertThat(deleted.getDeletedBy()).isEqualTo("manager1");
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    /** 연결된 계약·인증 이름을 표시하고 이전 이력으로 바뀌어도 자료가 남는다. */
    @Test
    void 계약과_인증_자료를_함께_보여주고_이력도_보존한다() throws Exception {
        idOf(mvc.perform(multipart("/api/suppliers/{id}/files", a.getId()).file(pdf()).param("contractId", contract.getId().toString()))
                .andExpect(jsonPath("$.contractName").value("FILE-CT · 자료 계약")));
        idOf(mvc.perform(multipart("/api/suppliers/{id}/files", a.getId()).file(pdf()).param("certificationId", certification.getId().toString()))
                .andExpect(jsonPath("$.certificationName").value(containsString("FILE-CERT"))));
        tx.executeWithoutResult(s -> {
            contracts.findById(contract.getId()).orElseThrow().markRenewed();
            certifications.findById(certification.getId()).orElseThrow().markRenewed();
        });
        mvc.perform(get("/api/suppliers/{id}/files", a.getId())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/suppliers/{id}/files", a.getId()).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].contractId").value(contract.getId()));
    }

    /** 다른 업체·없는 대상·중복 연결은 파일을 저장하기 전에 거절한다. */
    @Test
    void 잘못된_연결은_파일을_남기지_않는다() throws Exception {
        long before = files.count();
        mvc.perform(multipart("/api/suppliers/{id}/files", b.getId()).file(pdf()).param("contractId", contract.getId().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/suppliers/{id}/files", b.getId()).file(pdf()).param("certificationId", certification.getId().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/suppliers/{id}/files", a.getId()).file(pdf())
                        .param("contractId", contract.getId().toString()).param("certificationId", certification.getId().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/suppliers/{id}/files", a.getId()).file(pdf()).param("contractId", "-1"))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/suppliers/-1/files").file(pdf())).andExpect(status().isNotFound());
        assertThat(files.count()).isEqualTo(before);
        mvc.perform(get("/api/suppliers/{id}/files", a.getId()).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/suppliers/{id}/files", a.getId()).param("page", "-1")).andExpect(status().isBadRequest());
    }

    /** 조회자는 목록·다운로드만 가능하고 익명은 목록에도 접근하지 못한다. */
    @Test
    void 조회자의_올리기와_삭제를_서버에서_차단한다() throws Exception {
        long id = idOf(mvc.perform(multipart("/api/suppliers/{id}/files", a.getId()).file(pdf())));
        mvc.perform(get("/api/suppliers/{id}/files", a.getId()).with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(multipart("/api/suppliers/{id}/files", a.getId()).file(pdf()).with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/files/{id}", id).with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/suppliers/{id}/files", a.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
