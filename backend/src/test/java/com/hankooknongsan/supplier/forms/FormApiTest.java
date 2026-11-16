package com.hankooknongsan.supplier.forms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.file.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 실제 PostgreSQL과 디스크로 게시글·다중 첨부의 권한, 원자성, 삭제를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class FormApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StoredFileRepository files;
    @Autowired FormService service;
    private static final byte[] PDF = "%PDF-1.7\n양식 테스트\n%%EOF".getBytes(StandardCharsets.UTF_8);

    /** JSON 메타데이터를 multipart의 post 파트로 만든다. */
    private MockMultipartFile post(String title, String category, List<Long> removeIds) {
        var body = java.util.Map.of("title", title, "description", "양식 설명", "category", category, "removeFileIds", removeIds);
        return new MockMultipartFile("post", "", "application/json", mapper.writeValueAsBytes(body));
    }

    /** 파일 정책을 통과하는 가상 PDF다. */
    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", PDF);
    }

    /** 여러 첨부를 가진 게시글을 만들고 응답을 읽는다. */
    private JsonNode create(String title) throws Exception {
        return mapper.readTree(mvc.perform(multipart("/api/forms").file(post(title, "CONTRACT", List.of()))
                        .file(pdf("계약서.pdf")).file(pdf("부속서.pdf")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** 게시글·첨부·감사정보를 조회하고 파일 내용은 기존 다운로드 API로 받는다. */
    @Test
    void 여러_첨부와_게시글을_함께_등록한다() throws Exception {
        var created = create("계약 양식 " + System.nanoTime());
        long id = created.path("id").asLong();
        assertThat(created.path("files").size()).isEqualTo(2);
        mvc.perform(get("/api/forms/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value("manager1"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.files[0].originalName").value("계약서.pdf"));
        mvc.perform(get("/api/files/{id}/download", created.path("files").get(0).path("id").asLong()))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
    }

    /** 제목 검색·분류 필터·페이지가 함께 적용되고 최신 등록순으로 나온다. */
    @Test
    void 제목과_분류로_검색하고_페이지를_나눈다() throws Exception {
        String keyword = "검색" + System.nanoTime();
        create(keyword + " 첫째");
        long newest = create(keyword + " 둘째").path("id").asLong();
        mvc.perform(multipart("/api/forms").file(post(keyword + " 점검", "CHECKLIST", List.of())))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/forms").param("keyword", keyword).param("category", "CONTRACT").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].id").value(newest));
        mvc.perform(get("/api/forms").param("keyword", keyword).param("category", "CHECKLIST"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/forms").param("keyword", keyword).param("category", "CONTRACT").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    /** 삭제 지정 첨부만 교체하고 나머지 파일은 그대로 유지한다. */
    @Test
    void 수정하면서_첨부를_교체하고_나머지는_보존한다() throws Exception {
        var old = create("교체 전");
        long id = old.path("id").asLong();
        long removed = old.path("files").get(0).path("id").asLong();
        long retained = old.path("files").get(1).path("id").asLong();
        mvc.perform(multipart(HttpMethod.PUT, "/api/forms/{id}", id)
                        .file(post("교체 후", "OTHER", List.of(removed))).file(pdf("새양식.pdf")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("교체 후"))
                .andExpect(jsonPath("$.category").value("OTHER")).andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.files[0].id").value(retained));
        mvc.perform(get("/api/files/{id}/download", removed)).andExpect(status().isNotFound());
        assertThat(files.findById(removed).orElseThrow().getDeletedBy()).isEqualTo("manager1");
        mvc.perform(get("/api/files/{id}/download", retained)).andExpect(status().isOk());
    }

    /** 남의 첨부 ID를 삭제 대상으로 넘겨도 어느 게시글도 바뀌지 않는다. */
    @Test
    void 다른_게시글의_첨부를_삭제할_수_없다() throws Exception {
        var a = create("업무 A");
        var b = create("업무 B");
        long other = b.path("files").get(0).path("id").asLong();
        mvc.perform(multipart(HttpMethod.PUT, "/api/forms/{id}", a.path("id").asLong())
                        .file(post("변경 시도", "OTHER", List.of(other))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("removeFileIds"));
        mvc.perform(get("/api/forms/{id}", a.path("id").asLong())).andExpect(jsonPath("$.title").value("업무 A"));
        mvc.perform(get("/api/files/{id}/download", other)).andExpect(status().isOk());
    }

    /** 공통 API로 먼저 삭제한 파일도 제외하고 게시글의 나머지 첨부까지 함께 삭제한다. */
    @Test
    void 게시글_삭제시_모든_첨부의_다운로드를_차단한다() throws Exception {
        var created = create("삭제 대상 " + System.nanoTime());
        long id = created.path("id").asLong();
        mvc.perform(delete("/api/files/{id}", created.path("files").get(0).path("id").asLong()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/forms/{id}", id)).andExpect(jsonPath("$.files.length()").value(1));
        mvc.perform(delete("/api/forms/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/forms/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/forms").param("keyword", created.path("title").asText()))
                .andExpect(jsonPath("$.totalElements").value(0));
        for (var file : created.path("files")) {
            mvc.perform(get("/api/files/{id}/download", file.path("id").asLong())).andExpect(status().isNotFound());
            assertThat(files.findById(file.path("id").asLong()).orElseThrow().isDeleted()).isTrue();
        }
    }

    /** 뒤 파일이 잘못돼도 앞 파일의 DB 기록과 디스크 내용이 남지 않는다. */
    @Test
    void 등록_중_파일_하나가_실패하면_모두_취소한다() throws Exception {
        String title = "실패 등록 " + System.nanoTime();
        long before = files.count();
        Set<Path> disk = diskFiles();
        mvc.perform(multipart("/api/forms").file(post(title, "OTHER", List.of())).file(pdf("정상.pdf"))
                        .file(new MockMultipartFile("files", "위험.exe", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isBadRequest());
        assertThat(files.count()).isEqualTo(before);
        assertThat(diskFiles()).isEqualTo(disk);
        mvc.perform(get("/api/forms").param("keyword", title)).andExpect(jsonPath("$.totalElements").value(0));
    }

    /** 실패한 교체 요청은 제목 변경·기존 파일 삭제·새 파일 저장을 모두 되돌린다. */
    @Test
    void 수정_실패시_기존_내용과_파일을_보존한다() throws Exception {
        var created = create("유지할 제목");
        long fileId = created.path("files").get(0).path("id").asLong();
        long before = files.count();
        Set<Path> disk = diskFiles();
        mvc.perform(multipart(HttpMethod.PUT, "/api/forms/{id}", created.path("id").asLong())
                        .file(post("바뀌면 안됨", "OTHER", List.of(fileId))).file(pdf("정상.pdf"))
                        .file(new MockMultipartFile("files", "빈파일.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest());
        assertThat(files.count()).isEqualTo(before);
        assertThat(diskFiles()).isEqualTo(disk);
        mvc.perform(get("/api/forms/{id}", created.path("id").asLong()))
                .andExpect(jsonPath("$.title").value("유지할 제목")).andExpect(jsonPath("$.files.length()").value(2));
        mvc.perform(get("/api/files/{id}/download", fileId)).andExpect(status().isOk());
    }

    /** 조회자는 읽기만 가능하고 익명 접근과 입력 범위 초과는 거절한다. */
    @Test
    void 권한과_잘못된_입력을_검증한다() throws Exception {
        long id = create("권한 검증").path("id").asLong();
        mvc.perform(get("/api/forms/{id}", id).with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(multipart("/api/forms").file(post("금지", "OTHER", List.of())).with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, "/api/forms/{id}", id).file(post("금지", "OTHER", List.of())).with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/forms/{id}", id).with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/forms").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/forms").file(post(" ", "OTHER", List.of()))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/forms").file(post("x".repeat(201), "OTHER", List.of()))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/forms").file(post("제목", "UNKNOWN", List.of()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/forms").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/forms").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/forms").param("category", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/forms/-1")).andExpect(status().isNotFound());
    }

    /** 동시에 추가한 파일이 서로 덮어써지지 않고 같은 게시글에 모두 남는다. */
    @Test
    void 동시_첨부_추가를_직렬화한다() throws Exception {
        long id = create("동시 추가").path("id").asLong();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return service.update(id, new FormDtos.PostRequest("수정 A", "", "CONTRACT", List.of()),
                        List.of(pdf("추가A.pdf")), "manager1");
            });
            var b = pool.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return service.update(id, new FormDtos.PostRequest("수정 B", "", "CONTRACT", List.of()),
                        List.of(pdf("추가B.pdf")), "manager2");
            });
            a.get(20, java.util.concurrent.TimeUnit.SECONDS);
            b.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(service.get(id).files()).extracting(f -> f.originalName())
                .containsExactlyInAnyOrder("계약서.pdf", "부속서.pdf", "추가A.pdf", "추가B.pdf");
    }

    /** 첨부만 바꿔도 수정 시각이 갱신되고 중복 삭제 ID는 기존 파일을 손상시키지 않는다. */
    @Test
    void 첨부만_추가해도_감사정보를_갱신한다() throws Exception {
        var old = create("첨부만 변경");
        long id = old.path("id").asLong();
        var result = mvc.perform(multipart(HttpMethod.PUT, "/api/forms/{id}", id)
                        .file(post("첨부만 변경", "CONTRACT", List.of())).file(pdf("추가.pdf")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedBy").value("manager1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(java.time.LocalDateTime.parse(mapper.readTree(result).path("updatedAt").asText()))
                .isAfter(java.time.LocalDateTime.parse(old.path("updatedAt").asText()));
        long fileId = old.path("files").get(0).path("id").asLong();
        mvc.perform(multipart(HttpMethod.PUT, "/api/forms/{id}", id)
                        .file(post("중복 삭제", "OTHER", List.of(fileId, fileId))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/files/{id}/download", fileId)).andExpect(status().isOk());
    }

    /** 롤백 전후 실제 저장 파일 집합을 비교한다. 빈 날짜 폴더는 제외한다. */
    private Set<Path> diskFiles() throws Exception {
        try (var paths = Files.walk(Path.of("build/test-files"))) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toSet());
        }
    }
}
