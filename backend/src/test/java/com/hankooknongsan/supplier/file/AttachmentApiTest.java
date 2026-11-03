package com.hankooknongsan.supplier.file;

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

/** 계약·인증 창·목록에서 쓰는 첨부파일 조회 API (FR-021, FR-012)를 실제 DB로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class AttachmentApiTest {
    @Autowired MockMvc mvc;
    @Autowired SupplierRepository suppliers;
    @Autowired ContractRepository contracts;
    @Autowired CertificationRepository certifications;
    @Autowired CertTypeRepository certTypes;
    private static final byte[] PDF = "%PDF-1.7\n첨부 테스트\n%%EOF".getBytes(StandardCharsets.UTF_8);

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "첨부.pdf", "application/pdf", PDF);
    }

    /** 계약·인증 하나씩 만들고 각각 자료를 올린다. */
    private Contract[] setUpContracts() {
        Supplier s = suppliers.save(new Supplier("첨부 업체", SupplierStatus.ACTIVE));
        Contract c1 = new Contract(s);
        c1.update(s, "ATT-1", "첨부 계약 1", LocalDate.now(), LocalDate.now().plusYears(1), false, null, null, null);
        c1 = contracts.save(c1);
        Contract c2 = new Contract(s);
        c2.update(s, "ATT-2", "첨부 계약 2(자료 없음)", LocalDate.now(), LocalDate.now().plusYears(1), false, null, null, null);
        c2 = contracts.save(c2);
        return new Contract[]{c1, c2};
    }

    /** 계약 창·목록에서 그 계약에 연결된 자료만 받아온다. 자료가 없는 계약은 빈 목록이다. */
    @Test
    void 계약의_첨부파일_목록을_조회한다() throws Exception {
        Contract[] cs = setUpContracts();
        mvc.perform(multipart("/api/suppliers/{id}/files", cs[0].getSupplier().getId()).file(pdf())
                        .param("contractId", cs[0].getId().toString()))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/suppliers/{id}/files", cs[0].getSupplier().getId()).file(pdf())
                        .param("contractId", cs[0].getId().toString()))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/contracts/{id}/files", cs[0].getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].originalName").value("첨부.pdf"));
        mvc.perform(get("/api/contracts/{id}/files", cs[1].getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** 인증 창·목록에서 그 인증에 연결된 자료만 받아온다. */
    @Test
    void 인증의_첨부파일_목록을_조회한다() throws Exception {
        Supplier s = suppliers.save(new Supplier("인증 첨부 업체", SupplierStatus.ACTIVE));
        Certification cert = new Certification(s);
        cert.update(s, certTypes.findById("HACCP").orElseThrow(), "ATT-CERT", LocalDate.now(),
                LocalDate.now().plusYears(1), null, null, null);
        cert = certifications.save(cert);

        mvc.perform(multipart("/api/suppliers/{id}/files", s.getId()).file(pdf())
                        .param("certificationId", cert.getId().toString()))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/certifications/{id}/files", cert.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** 목록 화면에서 첨부 아이콘을 한 번에 그리기 위한 계약별 개수. 자료가 없는 계약은 결과에 아예 없다(0으로 본다). */
    @Test
    void 계약_여러_건의_첨부_개수를_한번에_센다() throws Exception {
        Contract[] cs = setUpContracts();
        mvc.perform(multipart("/api/suppliers/{id}/files", cs[0].getSupplier().getId()).file(pdf())
                        .param("contractId", cs[0].getId().toString()))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/contracts/files/counts").param("ids", cs[0].getId().toString(), cs[1].getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['" + cs[0].getId() + "']").value(1))
                .andExpect(jsonPath("$.['" + cs[1].getId() + "']").doesNotExist());

        // 화면은 ids를 콤마로 이어 하나의 값으로 보낸다(배열 파라미터 형식 차이를 피하기 위함). 이 형식도 같은 결과여야 한다.
        mvc.perform(get("/api/contracts/files/counts").param("ids", cs[0].getId() + "," + cs[1].getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['" + cs[0].getId() + "']").value(1))
                .andExpect(jsonPath("$.['" + cs[1].getId() + "']").doesNotExist());
    }
}
