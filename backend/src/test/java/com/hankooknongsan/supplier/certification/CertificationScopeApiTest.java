package com.hankooknongsan.supplier.certification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.notification.NotificationRepository;
import com.hankooknongsan.supplier.notification.job.NotificationScanService;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

/** 적용 대상 저장·검색·갱신·권한과 기존 만료 안내의 호환성을 실제 DB로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class CertificationScopeApiTest {

    static final LocalDate TODAY = LocalDate.of(2060, 1, 15);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired AppUserRepository users;
    @Autowired CertTypeRepository certTypes;
    @Autowired AppClock clock;
    @Autowired NotificationScanService scan;
    @Autowired NotificationRepository notifications;
    Long supplierId;
    Long ownerId;

    /** 다른 테스트와 구분되는 업체·담당자와 고정된 만료 기준일을 준비한다. */
    @BeforeEach
    void setUp() {
        clock.setToday(TODAY);
        supplierId = suppliers.save(new Supplier("적용대상 검증업체", SupplierStatus.ACTIVE)).getId();
        ownerId = users.save(new AppUser("scope-" + UUID.randomUUID(), "{noop}x", "대상 담당자", null, Role.MANAGER)).getId();
    }

    /** 다른 테스트에서 실제 기준일을 사용하도록 복원한다. */
    @AfterEach
    void tearDown() {
        clock.setToday(null);
    }

    /** 가상 인증 요청을 만든다. null도 JSON에 포함해 미입력 처리 경계를 검증한다. */
    Map<String, Object> request(String scope) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("supplierId", supplierId);
        req.put("certTypeCode", "HACCP");
        req.put("certificateNumber", "S-" + UUID.randomUUID());
        req.put("scope", scope);
        req.put("issuedDate", TODAY.minusYears(1).toString());
        req.put("expiryDate", TODAY.plusDays(5).toString());
        req.put("ownerId", ownerId);
        return req;
    }

    /** 요청을 실제 등록 API로 전달한다. */
    ResultActions create(Map<String, Object> req) throws Exception {
        return mvc.perform(post("/api/certifications").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)));
    }

    /** 저장 응답에서 생성된 인증 ID를 읽는다. */
    long idOf(ResultActions response) throws Exception {
        return mapper.readTree(response.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("item").path("id").asLong();
    }

    /** 앞뒤 공백을 제거한 적용 대상이 목록과 상세에도 유지된다. */
    @Test
    void 적용_대상을_공백_정리해_저장하고_조회한다() throws Exception {
        long id = idOf(create(request("  잼류  ")).andExpect(jsonPath("$.item.scope").value("잼류")));
        mvc.perform(get("/api/certifications/" + id)).andExpect(jsonPath("$.scope").value("잼류"));
        mvc.perform(get("/api/certifications").param("supplierId", supplierId.toString()))
                .andExpect(jsonPath("$.content[0].scope").value("잼류"));
    }

    /** null·빈 문자열·공백은 대상 미지정으로 저장되며 기존 표시 방식을 유지한다. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 빈_적용_대상은_null로_저장한다(String scope) throws Exception {
        long id = idOf(create(request(scope)).andExpect(jsonPath("$.item.scope").doesNotExist()));
        mvc.perform(get("/api/certifications/" + id)).andExpect(jsonPath("$.scope").doesNotExist());
    }

    /** 적용 대상 필드가 없던 클라이언트도 등록·수정·갱신할 수 있다. */
    @Test
    void 기존_요청은_적용_대상_없이_수정하고_갱신할_수_있다() throws Exception {
        var req = request(null);
        req.remove("scope");
        long id = idOf(create(req));
        mvc.perform(put("/api/certifications/" + id).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.item.scope").doesNotExist());
        mvc.perform(post("/api/certifications/" + id + "/renew").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.item.scope").doesNotExist())
                .andExpect(jsonPath("$.item.prevId").value(id));
    }

    /** 200자까지 허용하고 초과는 적용 대상 필드 오류로 알려준다. */
    @Test
    void 적용_대상_길이를_200자로_제한한다() throws Exception {
        create(request("가".repeat(200))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.scope").value("가".repeat(200)));
        create(request("가".repeat(201))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("scope"));
    }

    /** 대상 검색이 기존 번호·업체 검색, 종류·담당자·상태·업체 필터와 페이지 분할을 보존한다. */
    @Test
    void 대상_키워드와_기존_검색조건을_함께_적용한다() throws Exception {
        var first = request("제품 Alpha");
        long firstId = idOf(create(first));
        long secondId = idOf(create(request("제품 Beta")));
        var otherType = request("제품 Gamma");
        otherType.put("certTypeCode", "GAP");
        create(otherType).andExpect(status().isCreated());
        var otherSupplier = request("제품 Delta");
        otherSupplier.put("supplierId", suppliers.save(new Supplier("다른 업체", SupplierStatus.ACTIVE)).getId());
        create(otherSupplier).andExpect(status().isCreated());
        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/api/certifications").param("supplierId", supplierId.toString()).param("keyword", "  제품  ")
                            .param("certType", "HACCP").param("ownerId", ownerId.toString()).param("renewalStatus", "NONE")
                            .param("page", Integer.toString(page)).param("size", "1"))
                    .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(page == 0 ? firstId : secondId));
        }
        for (String keyword : new String[] {"alpha", (String) first.get("certificateNumber"), "검증업체"}) {
            mvc.perform(get("/api/certifications").param("supplierId", supplierId.toString()).param("keyword", keyword))
                    .andExpect(jsonPath("$.content[0].id").value(firstId));
        }
        mvc.perform(patch("/api/certifications/" + firstId + "/renewal-status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"renewalStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/certifications").param("supplierId", supplierId.toString()).param("keyword", "제품")
                        .param("certType", "HACCP").param("renewalStatus", "NONE"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(secondId));
    }

    /** 갱신 대상 변경과 삭제가 이전 인증의 값과 연결 이력을 바꾸지 않는다. */
    @Test
    void 갱신_대상을_수정해도_이전_값을_보존한다() throws Exception {
        long oldId = idOf(create(request("공장")));
        var req = request("제품 A");
        long newId = idOf(mvc.perform(post("/api/certifications/" + oldId + "/renew")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.item.scope").value("제품 A")).andExpect(jsonPath("$.item.prevId").value(oldId)));
        mvc.perform(get("/api/certifications/" + oldId)).andExpect(jsonPath("$.scope").value("공장"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/certifications").param("supplierId", supplierId.toString()).param("keyword", "공장"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/certifications").param("supplierId", supplierId.toString()).param("keyword", "공장").param("includeInactive", "true"))
                .andExpect(jsonPath("$.content[0].id").value(oldId));
        req.put("scope", " ");
        mvc.perform(put("/api/certifications/" + newId).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.item.scope").doesNotExist());
        mvc.perform(get("/api/certifications/" + oldId)).andExpect(jsonPath("$.scope").value("공장"));
    }

    /** 기존 만료 안내 3종과 알림이 대상 유무에 따라 인증을 구분한다. */
    @Test
    void 만료_화면과_알림에_적용_대상을_함께_표시한다() throws Exception {
        supplierId = suppliers.save(new Supplier("상".repeat(100), SupplierStatus.ACTIVE)).getId();
        String type = "종".repeat(100);
        certTypes.save(new CertType("SCOPE-LONG", type, type, 1000));
        String scope = "가".repeat(200);
        var withRequest = request(scope);
        withRequest.put("certTypeCode", "SCOPE-LONG");
        withRequest.put("certificateNumber", "N".repeat(50));
        long withScope = idOf(create(withRequest));
        var withoutRequest = request(null);
        withoutRequest.put("certTypeCode", "SCOPE-LONG");
        long withoutScope = idOf(create(withoutRequest));
        for (String path : new String[] {"/api/expiries", "/api/expiries/calendar", "/api/expiries/board"}) {
            mvc.perform(get(path).param("ownerId", ownerId.toString()).param("targetType", "CERTIFICATION")
                            .param("from", TODAY.toString()).param("to", TODAY.plusDays(30).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.targetId == " + withScope + ")].name", contains(type + " · " + scope)))
                    .andExpect(jsonPath("$[?(@.targetId == " + withoutScope + ")].name", contains(type)));
        }
        scan.scan(TODAY);
        var matching = notifications.findAll().stream().filter(n -> n.getTargetType() == TargetType.CERTIFICATION
                && (n.getTargetId() == withScope || n.getTargetId() == withoutScope)).toList();
        assertThat(matching).isNotEmpty();
        assertThat(matching.stream().filter(n -> n.getTargetId() == withScope)).isNotEmpty().allSatisfy(n -> {
            assertThat(n.getTitle()).contains(type + " · " + scope);
            assertThat(n.getMessage()).contains(type + " · " + scope);
        });
        assertThat(matching.stream().filter(n -> n.getTargetId() == withoutScope)).isNotEmpty().allSatisfy(n -> {
            assertThat(n.getMessage()).contains(type + "(").doesNotContain("null", " · (");
        });
    }

    /** 적용 대상 검색으로 내려받은 엑셀에도 별도 열과 입력값이 포함된다. */
    @Test
    void 엑셀에도_검색된_적용_대상을_표시한다() throws Exception {
        create(request("제품 A")).andExpect(status().isCreated());
        create(request("공장")).andExpect(status().isCreated());
        byte[] bytes = mvc.perform(get("/api/certifications/excel").param("supplierId", supplierId.toString()).param("keyword", "제품 A"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("적용 대상");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("제품 A");
        }
    }

    /** 조회자는 적용 대상을 볼 수 있지만 인증의 등록·수정·갱신 권한은 없다. */
    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void 조회자는_적용_대상_조회만_할_수_있다() throws Exception {
        String body = mapper.writeValueAsString(request("공장"));
        long id = idOf(mvc.perform(post("/api/certifications").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)));
        mvc.perform(get("/api/certifications/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("공장"));
        create(request("제품 A")).andExpect(status().isForbidden());
        mvc.perform(put("/api/certifications/" + id).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/certifications/" + id + "/renew").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }
}
