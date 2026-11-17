package com.hankooknongsan.supplier.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.file.StoredFileRepository;
import com.hankooknongsan.supplier.file.storage.FileStorage;
import com.hankooknongsan.supplier.notification.Channel;
import com.hankooknongsan.supplier.notification.Notification;
import com.hankooknongsan.supplier.notification.NotificationRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 협력업체·계약·인증 삭제 API 통합 테스트 (#44).
 * 지운 표시만 남는지, 조회·알림·첨부에서 빠지는지, 갱신 건을 지우면 이전 건이 되살아나는지,
 * 계약·인증이 남은 업체는 막는지, 조회자는 지울 수 없는지를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class DeleteApiTest {

    private static final byte[] PDF = "%PDF-1.7\n삭제 테스트\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired AppUserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired StoredFileRepository files;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppClock clock;
    @Autowired FileStorage storage;

    Long supplierId;

    /** 테스트마다 새 협력업체를 만든다. */
    @BeforeEach
    void setUp() {
        supplierId = suppliers.save(new Supplier("삭제 테스트 업체", SupplierStatus.ACTIVE)).getId();
    }

    /** 등록·갱신 응답에서 항목 ID를 꺼낸다. */
    long itemId(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("item").path("id").asLong();
    }

    /** 계약 등록 또는 갱신 요청을 보내고 새 계약 ID를 돌려준다. */
    long contract(String url) throws Exception {
        return itemId(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("""
                {"supplierId":%d,"contractNumber":"DEL-%s","title":"삭제 테스트 계약","startDate":"2026-01-01","endDate":"2026-12-31"}"""
                .formatted(supplierId, UUID.randomUUID()))));
    }

    /** 인증 등록 또는 갱신 요청을 보내고 새 인증 ID를 돌려준다. */
    long certification(String url) throws Exception {
        return itemId(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("""
                {"supplierId":%d,"certTypeCode":"HACCP","certificateNumber":"DEL-%s","issuedDate":"2026-01-01","expiryDate":"2026-12-31"}"""
                .formatted(supplierId, UUID.randomUUID()))));
    }

    /** 업체 자료 탭으로 파일을 올리고 파일 ID를 돌려준다. param이 있으면 계약·인증에 연결한다. */
    long upload(String param, Long targetId) throws Exception {
        var request = multipart("/api/suppliers/{id}/files", supplierId)
                .file(new MockMultipartFile("file", "삭제 테스트.pdf", "application/pdf", PDF));
        if (param != null) request.param(param, targetId.toString());
        return mapper.readTree(mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("file").path("id").asLong();
    }

    /** 삭제한 계약은 조회·업체 건수·첨부·알림에서 빠지지만, DB에는 지운 사람과 함께 남는다. */
    @Test
    void deletedContractDisappearsButRowRemains() throws Exception {
        long id = contract("/api/contracts");
        long fileId = upload("contractId", id);
        AppUser recipient = users.save(new AppUser("del-" + UUID.randomUUID(), "{noop}x", "알림담당", "d@test.local", Role.MANAGER));
        Notification n = new Notification(TargetType.CONTRACT, id, 30, LocalDate.of(2026, 12, 31), recipient,
                suppliers.findById(supplierId).orElseThrow(), "만료 예정", "삭제 테스트", clock.now());
        n.addDelivery(Channel.IN_APP, clock.now());
        long notificationId = notifications.save(n).getId();

        mvc.perform(delete("/api/contracts/" + id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/contracts/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/contracts").param("supplierId", supplierId.toString()).param("includeInactive", "true"))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(jsonPath("$.contractCount").value(0));
        mvc.perform(get("/api/suppliers/{id}/files", supplierId)).andExpect(jsonPath("$.content", hasSize(0)));
        assertThat(files.findByIdAndDeletedAtIsNull(fileId)).isEmpty();
        assertThat(notifications.findById(notificationId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where notification_id = ?",
                Long.class, notificationId)).isZero();
        assertThat(jdbc.queryForObject("select deleted_by from contract where id = ?", String.class, id)).isEqualTo("manager1");
        mvc.perform(delete("/api/contracts/" + id)).andExpect(status().isNotFound());
    }

    /** 갱신한 계약을 지우면 이전 계약이 미착수 상태의 현재 계약으로 돌아오고, 이전 이력은 직접 지울 수 없다. */
    @Test
    void deletingRenewedContractRestoresPrevious() throws Exception {
        long oldId = contract("/api/contracts");
        long newId = contract("/api/contracts/" + oldId + "/renew");

        mvc.perform(delete("/api/contracts/" + oldId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("이전 이력")));
        mvc.perform(delete("/api/contracts/" + newId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/contracts/" + oldId)).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.renewalStatus").value("NONE"));
        mvc.perform(delete("/api/contracts/" + oldId)).andExpect(status().isNoContent());
    }

    /** 인증도 계약과 같은 규칙으로 삭제되고, 갱신 건을 지우면 이전 인증이 되살아난다. */
    @Test
    void deletingRenewedCertificationRestoresPrevious() throws Exception {
        long oldId = certification("/api/certifications");
        long newId = certification("/api/certifications/" + oldId + "/renew");
        long fileId = upload("certificationId", newId);

        mvc.perform(delete("/api/certifications/" + oldId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/certifications/" + newId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/certifications/" + newId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/certifications/" + oldId)).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.renewalStatus").value("NONE"));
        mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(jsonPath("$.certCount").value(1));
        assertThat(files.findByIdAndDeletedAtIsNull(fileId)).isEmpty();
    }

    /** 계약·인증이 남은 업체는 건수와 함께 막고, 모두 지운 뒤에는 업체와 일반 자료가 함께 삭제된다. */
    @Test
    void supplierIsBlockedUntilContractsAndCertificationsAreDeleted() throws Exception {
        long contractId = contract("/api/contracts");
        long certificationId = certification("/api/certifications");
        long fileId = upload(null, null);

        mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("계약 1건, 인증 1건")));
        mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(status().isOk());

        mvc.perform(delete("/api/contracts/" + contractId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/certifications/" + certificationId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/suppliers").param("keyword", "삭제 테스트 업체"))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(supplierId), hasSize(0)));
        assertThat(files.findByIdAndDeletedAtIsNull(fileId)).isEmpty();
        assertThat(jdbc.queryForObject("select deleted_by from supplier where id = ?", String.class, supplierId)).isEqualTo("manager1");
    }

    /** 조회자는 협력업체·계약·인증을 삭제할 수 없다. */
    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerCannotDelete() throws Exception {
        mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/contracts/1")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/certifications/1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(status().isOk());
    }

    /** 관리자 권한으로 되살리기를 요청한다. */
    ResultActions restore(String path) throws Exception {
        return mvc.perform(post(path + "/restore").with(user("admin").roles("ADMIN")));
    }

    /** 갱신 건을 되살리면 함께 지워진 첨부가 돌아오고 이전 계약은 다시 갱신완료 이력이 된다. 담당자는 되살릴 수 없다. (#72) */
    @Test
    void restoredContractBringsBackFilesAndPreviousBecomesHistoryAgain() throws Exception {
        long oldId = contract("/api/contracts");
        long newId = contract("/api/contracts/" + oldId + "/renew");
        long fileId = upload("contractId", newId);
        long earlierFileId = upload("contractId", newId);
        mvc.perform(delete("/api/contracts/" + newId)).andExpect(status().isNoContent());
        // 계약보다 먼저 따로 지운 파일은 되살리지 않아야 한다.
        jdbc.update("update stored_file set deleted_at = '2000-01-01' where id = ?", earlierFileId);
        mvc.perform(get("/api/suppliers/{id}/changes", supplierId))
                .andExpect(jsonPath("$.content[?(@.action == 'DELETE')].restorable").value(true));

        mvc.perform(post("/api/contracts/" + newId + "/restore")).andExpect(status().isForbidden());
        restore("/api/contracts/" + newId).andExpect(status().isNoContent());

        mvc.perform(get("/api/contracts/" + newId)).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/contracts/" + oldId)).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.renewalStatus").value("DONE"));
        assertThat(files.findByIdAndDeletedAtIsNull(fileId)).isPresent();
        assertThat(files.findByIdAndDeletedAtIsNull(earlierFileId)).isEmpty();
        mvc.perform(get("/api/suppliers/{id}/changes", supplierId))
                .andExpect(jsonPath("$.content[0].action").value("RESTORE"))
                .andExpect(jsonPath("$.content[0].actor").value("admin"))
                .andExpect(jsonPath("$.content[?(@.action == 'DELETE')].restorable").value(false));
        restore("/api/contracts/" + newId).andExpect(status().isNotFound());
    }

    /** 이전 계약이 그 사이 다시 갱신되었으면 현재 계약이 둘이 되지 않도록 되살리기를 막는다. (#72) */
    @Test
    void restoreIsBlockedWhenPreviousWasRenewedAgain() throws Exception {
        long oldId = contract("/api/contracts");
        long newId = contract("/api/contracts/" + oldId + "/renew");
        mvc.perform(delete("/api/contracts/" + newId)).andExpect(status().isNoContent());
        contract("/api/contracts/" + oldId + "/renew");

        restore("/api/contracts/" + newId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("이전 계약")));
        mvc.perform(get("/api/contracts/" + newId)).andExpect(status().isNotFound());
    }

    /** 협력업체가 삭제된 상태면 그 업체의 인증은 되살릴 수 없고, 업체를 되살리면 자료와 함께 돌아온 뒤 인증도 되살릴 수 있다. (#72) */
    @Test
    void supplierMustBeRestoredBeforeItsCertification() throws Exception {
        long certificationId = certification("/api/certifications");
        long fileId = upload(null, null);
        mvc.perform(delete("/api/certifications/" + certificationId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isNoContent());

        restore("/api/certifications/" + certificationId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("협력업체를 먼저")));
        restore("/api/suppliers/" + supplierId).andExpect(status().isNoContent());
        mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(status().isOk());
        assertThat(files.findByIdAndDeletedAtIsNull(fileId)).isPresent();

        restore("/api/certifications/" + certificationId).andExpect(status().isNoContent());
        mvc.perform(get("/api/certifications/" + certificationId)).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(jsonPath("$.certCount").value(1));
    }

    /** 관리자 권한으로 영구삭제를 요청한다. */
    ResultActions purge(String path) throws Exception {
        return mvc.perform(delete(path + "/purge").with(user("admin").roles("ADMIN")));
    }

    /** 영구삭제하면 계약 행과 첨부(기록·저장소 내용)가 없어지고 되살릴 수 없으며, 이력에는 영구삭제 기록만 남는다. (#77) */
    @Test
    void purgedContractIsGoneWithItsFiles() throws Exception {
        long oldId = contract("/api/contracts");
        long newId = contract("/api/contracts/" + oldId + "/renew");
        long fileId = upload("contractId", newId);
        String storageKey = jdbc.queryForObject("select storage_key from stored_file where id = ?", String.class, fileId);
        mvc.perform(delete("/api/contracts/" + newId + "/purge").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/contracts/" + newId)).andExpect(status().isNoContent());
        assertThat(storage.load(storageKey).exists()).isTrue();
        mvc.perform(get("/api/admin/files/purge-count").param("contractId", String.valueOf(newId))
                .with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$").value(1));

        mvc.perform(delete("/api/contracts/" + newId + "/purge")).andExpect(status().isForbidden());
        purge("/api/contracts/" + newId).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from contract where id = ?", Long.class, newId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from stored_file where id = ?", Long.class, fileId)).isZero();
        assertThatThrownBy(() -> storage.load(storageKey)).hasMessageContaining("저장된 파일이 없습니다");
        mvc.perform(get("/api/contracts/" + oldId)).andExpect(jsonPath("$.active").value(true));
        restore("/api/contracts/" + newId).andExpect(status().isNotFound());
        mvc.perform(get("/api/suppliers/{id}/changes", supplierId))
                .andExpect(jsonPath("$.content[0].action").value("PURGE"))
                .andExpect(jsonPath("$.content[0].targetName").value("삭제 테스트 계약"))
                .andExpect(jsonPath("$.content[?(@.action == 'DELETE')].restorable").value(false));
    }

    /** 갱신 건과 이전 건을 모두 지운 뒤 이전 건을 먼저 영구삭제해도, 그것을 가리키던 갱신 건은 남아 되살릴 수 있다. (#77) */
    @Test
    void purgingPreviousKeepsDeletedRenewalRestorable() throws Exception {
        long oldId = certification("/api/certifications");
        long newId = certification("/api/certifications/" + oldId + "/renew");
        mvc.perform(delete("/api/certifications/" + newId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/certifications/" + oldId)).andExpect(status().isNoContent());

        purge("/api/certifications/" + oldId).andExpect(status().isNoContent());
        restore("/api/certifications/" + newId).andExpect(status().isNoContent());
        mvc.perform(get("/api/certifications/" + newId)).andExpect(jsonPath("$.active").value(true));
    }

    /** 삭제된 계약이 남은 협력업체는 영구삭제를 막고 건수를 알려 준다. 계약을 먼저 영구삭제하면 담당자·메모·자료와 함께 지워진다. (#77) */
    @Test
    void supplierPurgeIsBlockedWhileDeletedContractsRemain() throws Exception {
        long contractId = contract("/api/contracts");
        long fileId = upload(null, null);
        mvc.perform(post("/api/suppliers/{id}/notes", supplierId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"영구삭제 테스트 메모\",\"contactDate\":\"2026-01-01\"}")).andExpect(status().isCreated());
        // 필수 서류 예외(#85)가 있어도 업체를 영구삭제할 수 있어야 한다.
        jdbc.update("insert into doc_exemption (supplier_id, category_id, created_at, created_by) "
                + "select ?, min(id), now(), 'manager1' from file_category", supplierId);
        mvc.perform(delete("/api/contracts/" + contractId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isNoContent());

        purge("/api/suppliers/" + supplierId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("삭제된 계약 1건, 인증 0건")));
        purge("/api/contracts/" + contractId).andExpect(status().isNoContent());
        purge("/api/suppliers/" + supplierId).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from supplier where id = ?", Long.class, supplierId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from supplier_note where supplier_id = ?", Long.class, supplierId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from stored_file where id = ?", Long.class, fileId)).isZero();
        mvc.perform(get("/api/admin/changes").param("keyword", "삭제 테스트 업체").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.content[0].action").value("PURGE"));
    }
}
