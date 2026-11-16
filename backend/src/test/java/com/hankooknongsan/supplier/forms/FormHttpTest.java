package com.hankooknongsan.supplier.forms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.file.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/** MockMvc가 생략하는 실제 HTTP 세션과 multipart POST/PUT 파싱을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.servlet.multipart.max-request-size=1MB")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class FormHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired StoredFileRepository files;
    private static final String BOUNDARY = "FormsTestBoundary";

    /** 로그인 세션으로 JSON 파트·파일을 등록하고 PUT에서 기존 파일을 원자적으로 교체한다. */
    @Test
    void 실제_HTTP로_등록과_첨부_교체가_된다() throws Exception {
        try (var client = login()) {
            var created = send(client, "POST", "/api/forms", multipart("[]", 20));
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            var post = mapper.readTree(created.body());
            long id = post.path("id").asLong();
            long fileId = post.path("files").get(0).path("id").asLong();
            var updated = send(client, "PUT", "/api/forms/" + id, multipart("[" + fileId + "]", 30));
            assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
            assertThat(mapper.readTree(updated.body()).path("files").size()).isEqualTo(3);
            var oldDownload = client.send(HttpRequest.newBuilder(uri("/api/files/" + fileId + "/download")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(oldDownload.statusCode()).isEqualTo(404);
        }
    }

    /** 작은 테스트 한도로 실제 multipart 요청 합계 제한과 저장 방지를 검증한다. */
    @Test
    void 요청_전체_용량_제한을_적용한다() throws Exception {
        try (var client = login()) {
            long before = files.count();
            var response = send(client, "POST", "/api/forms", multipart("[]", 600 * 1024));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(413);
            assertThat(files.count()).isEqualTo(before);
        }
    }

    /** 독립 테스트 계정으로 실제 세션 로그인을 한다. */
    private HttpClient login() throws Exception {
        String username = "forms-http-" + System.nanoTime();
        users.save(new AppUser(username, encoder.encode("TestPass123!"), "HTTP 테스트", null, Role.MANAGER));
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var response = client.send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + username + "\",\"password\":\"TestPass123!\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return client;
    }

    /** 두 파일과 JSON post를 실제 multipart 바이트로 구성한다. */
    private byte[] multipart(String removeIds, int fileSize) throws Exception {
        var body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"post\"\r\nContent-Type: application/json\r\n\r\n"
                + "{\"title\":\"HTTP 양식\",\"category\":\"OTHER\",\"removeFileIds\":" + removeIds + "}\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] content = new byte[fileSize];
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.UTF_8), 0, content, 0, 9);
        for (int i = 0; i < 2; i++) {
            body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"form" + i
                    + ".pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(content);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    /** 실제 웹 서버에 multipart 요청을 보낸다. */
    private HttpResponse<String> send(HttpClient client, String method, String path, byte[] body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 임의 포트로 시작한 테스트 서버 주소를 만든다. */
    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
