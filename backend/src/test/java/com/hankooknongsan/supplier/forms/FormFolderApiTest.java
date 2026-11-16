package com.hankooknongsan.supplier.forms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import com.hankooknongsan.supplier.TestcontainersConfig;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 폴더 계층·동시 변경·양식 소속·삭제·권한을 실제 PostgreSQL에서 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "manager1", roles = "MANAGER")
class FormFolderApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    /** 테스트끼리 겹치지 않는 최상위 하위 폴더를 만든다. */
    private long folder(long parent, String name) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/form-folders").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("parentId", parent, "name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    /** 파일 없는 양식도 폴더 안의 자료로 취급한다. */
    private JsonNode form(long folder, String title) throws Exception {
        var part = new MockMultipartFile("post", "", "application/json", mapper.writeValueAsBytes(
                Map.of("title", title, "category", "OTHER", "folderId", folder)));
        return mapper.readTree(mvc.perform(multipart("/api/forms").file(part)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** 손자 폴더의 경로에 이름 변경이 즉시 반영되고 직접 자식만 조회된다. */
    @Test
    void 하위폴더와_상위경로를_조회하고_이름을_변경한다() throws Exception {
        long parent = folder(1, "계층" + System.nanoTime());
        long child = folder(parent, "점검표");
        long leaf = folder(child, "월별");
        mvc.perform(put("/api/form-folders/{id}", child).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"정기 점검표\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("정기 점검표"));
        mvc.perform(get("/api/form-folders/{id}", leaf)).andExpect(status().isOk())
                .andExpect(jsonPath("$.path.length()").value(4))
                .andExpect(jsonPath("$.path[0].id").value(1))
                .andExpect(jsonPath("$.path[2].name").value("정기 점검표"))
                .andExpect(jsonPath("$.children.length()").value(0));
        mvc.perform(get("/api/form-folders/{id}", parent)).andExpect(jsonPath("$.children.length()").value(1))
                .andExpect(jsonPath("$.children[0].id").value(child));
    }

    /** 같은 부모에서 공백·대소문자만 다른 이름은 충돌하며 다른 부모에서는 허용한다. */
    @Test
    void 같은_위치의_폴더명_중복을_생성과_변경에서_막는다() throws Exception {
        long parent = folder(1, "중복" + System.nanoTime());
        folder(parent, "HACCP");
        mvc.perform(post("/api/form-folders").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("parentId", parent, "name", " haccp "))))
                .andExpect(status().isBadRequest());
        long other = folder(parent, "기타");
        mvc.perform(put("/api/form-folders/{id}", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"HACCP\"}")).andExpect(status().isBadRequest());
        folder(other, "HACCP");
    }

    /** 하위 폴더와 파일 없는 양식이 있어도 폴더 삭제를 막고, 비운 뒤에만 삭제한다. */
    @Test
    void 비어있는_폴더만_삭제하고_삭제후_접근을_막는다() throws Exception {
        long parent = folder(1, "삭제" + System.nanoTime());
        long child = folder(parent, "하위");
        mvc.perform(delete("/api/form-folders/{id}", parent)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/form-folders/{id}", child)).andExpect(status().isNoContent());
        long post = form(parent, "보존할 양식").path("id").asLong();
        mvc.perform(delete("/api/form-folders/{id}", parent)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/forms/{id}", post)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/form-folders/{id}", parent)).andExpect(status().isNoContent());
        mvc.perform(get("/api/form-folders/{id}", parent)).andExpect(status().isNotFound());
        mvc.perform(get("/api/forms").param("folderId", Long.toString(parent))).andExpect(status().isNotFound());
        mvc.perform(post("/api/form-folders").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("parentId", parent, "name", "고아 폴더"))))
                .andExpect(status().isNotFound());
        var part = new MockMultipartFile("post", "", "application/json", mapper.writeValueAsBytes(
                Map.of("title", "고아 양식", "category", "OTHER", "folderId", parent)));
        mvc.perform(multipart("/api/forms").file(part)).andExpect(status().isNotFound());
    }

    /** 폴더를 지정한 목록은 해당 폴더만 검색하고 기존 전체 목록 계약은 유지한다. */
    @Test
    void 폴더별로_양식을_검색하고_다른_폴더와_구분한다() throws Exception {
        long parent = folder(1, "필터" + System.nanoTime());
        long child = folder(parent, "하위");
        String keyword = "양식" + System.nanoTime();
        form(parent, keyword + " 상위");
        var nested = form(child, keyword + " 하위");
        assertThat(nested.path("folderId").asLong()).isEqualTo(child);
        mvc.perform(get("/api/forms").param("keyword", keyword).param("folderId", Long.toString(parent)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/forms").param("keyword", keyword)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/forms/{id}", nested.path("id").asLong())).andExpect(jsonPath("$.folderId").value(child));
    }

    /** 기존 수정 요청의 폴더 생략은 위치를 유지하며 명시적인 다른 폴더로의 이동은 거절한다. */
    @Test
    void 폴더를_생략한_양식수정은_위치를_유지하고_이동을_막는다() throws Exception {
        long parent = folder(1, "위치유지" + System.nanoTime());
        long child = folder(parent, "하위");
        long id = form(child, "수정 전").path("id").asLong();
        var update = new MockMultipartFile("post", "", "application/json", mapper.writeValueAsBytes(
                Map.of("title", "수정 후", "category", "OTHER")));
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/forms/{id}", id).file(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.folderId").value(child));
        var move = new MockMultipartFile("post", "", "application/json", mapper.writeValueAsBytes(
                Map.of("title", "거절할 수정", "category", "OTHER", "folderId", parent)));
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/forms/{id}", id).file(move))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/forms/{id}", id)).andExpect(jsonPath("$.folderId").value(child))
                .andExpect(jsonPath("$.title").value("수정 후"));
    }

    /** 조회자는 탐색만 가능하고 익명·최상위 변경·잘못된 이름은 거절한다. */
    @Test
    void 권한과_최상위폴더와_폴더명을_검증한다() throws Exception {
        long id = folder(1, "권한" + System.nanoTime());
        mvc.perform(get("/api/form-folders/{id}", id).with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(post("/api/form-folders").with(user("viewer").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"금지\",\"parentId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/form-folders/{id}", id).with(user("viewer").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"금지\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/form-folders/{id}", id).with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/form-folders/1").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/form-folders/1")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/form-folders/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"변경\"}"))
                .andExpect(status().isBadRequest());
        for (String name : new String[]{" ", ".", "..", "a/b", "a\\b", "a\nb\nc", "a\u0085b", "x".repeat(101)}) {
            mvc.perform(post("/api/form-folders").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(Map.of("parentId", id, "name", name))))
                    .andExpect(status().isBadRequest());
        }
    }

    /** 동시에 동일 이름을 만들면 하나만 성공해 중복 폴더가 남지 않는다. */
    @Test
    void 동시_폴더생성도_중복을_막는다() throws Exception {
        long id = folder(1, "동시" + System.nanoTime());
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create = () -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/form-folders").with(user("manager1").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("parentId", id, "name", "중복"))))
                        .andReturn().getResponse().getStatus();
            };
            var a = pool.submit(create);
            var b = pool.submit(create);
            assertThat(java.util.List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 400);
        }
        mvc.perform(get("/api/form-folders/{id}", id)).andExpect(jsonPath("$.children.length()").value(1));
    }

    /** 부모 잠금 대기 전에 읽은 이름 때문에 뒤 요청의 변경이 누락되지 않아야 한다. */
    @Test
    void 동시_이름변경은_잠금후_최신_폴더를_사용한다() throws Exception {
        long parent = folder(1, "이름경합" + System.nanoTime());
        long child = folder(parent, "원래 이름");
        var responses = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                jdbc.queryForObject("select id from form_folder where id = ? for update", Long.class, parent);
                responses.add(pool.submit(() -> mvc.perform(put("/api/form-folders/{id}", child)
                                .with(user("manager1").roles("MANAGER")).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"먼저 변경한 이름\"}"))
                        .andReturn().getResponse().getStatus()));
                awaitFolderLocks(1);
                responses.add(pool.submit(() -> mvc.perform(put("/api/form-folders/{id}", child)
                                .with(user("manager1").roles("MANAGER")).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"원래 이름\"}"))
                        .andReturn().getResponse().getStatus()));
                awaitFolderLocks(2);
            });
            for (var response : responses) assertThat(response.get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        }
        mvc.perform(get("/api/form-folders/{id}", child)).andExpect(jsonPath("$.folder.name").value("원래 이름"));
    }

    /** 생성·삭제가 어느 순서로 잠금을 얻어도 살아 있는 양식이 삭제된 폴더에 남지 않는다. */
    @Test
    void 양식생성과_폴더삭제_경합은_잠금순서에_따라_하나만_성공한다() throws Exception {
        for (boolean createFirst : new boolean[]{true, false}) {
            long id = folder(1, "생성삭제경합" + System.nanoTime());
            java.util.concurrent.Callable<Integer> create = () -> {
                var part = new MockMultipartFile("post", "", "application/json", mapper.writeValueAsBytes(
                        Map.of("title", "경합 양식", "category", "OTHER", "folderId", id)));
                return mvc.perform(multipart("/api/forms").file(part).with(user("manager1").roles("MANAGER")))
                        .andReturn().getResponse().getStatus();
            };
            java.util.concurrent.Callable<Integer> remove = () -> mvc.perform(delete("/api/form-folders/{id}", id)
                            .with(user("manager1").roles("MANAGER")))
                    .andReturn().getResponse().getStatus();
            var responses = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                    jdbc.queryForObject("select id from form_folder where id = ? for update", Long.class, id);
                    responses.add(pool.submit(createFirst ? create : remove));
                    awaitFolderLocks(1);
                    responses.add(pool.submit(createFirst ? remove : create));
                    awaitFolderLocks(2);
                });
                assertThat(responses.get(0).get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(createFirst ? 201 : 204);
                assertThat(responses.get(1).get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(createFirst ? 400 : 404);
            }
            assertThat(jdbc.queryForObject("""
                    select count(*) from form_post p join form_folder f on p.folder_id = f.id
                    where p.folder_id = ? and p.deleted_at is null and f.deleted_at is not null
                    """, Integer.class, id)).isZero();
        }
    }

    /** 실제 PostgreSQL 잠금 대기를 확인해 요청 실행 순서를 고정한다. */
    private void awaitFolderLocks(int expected) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("select pg_stat_clear_snapshot()");
            Integer waiting = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock' and query like '%form_folder%'
                    """, Integer.class);
            if (waiting != null && waiting >= expected) return;
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        }
        throw new AssertionError("폴더 잠금 대기 요청이 " + expected + "개가 되지 않았습니다.");
    }
}
