package com.hankooknongsan.supplier.file;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HexFormat;

import com.hankooknongsan.supplier.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 첨부파일 API 통합 테스트 (#3).
 * 이슈 완료 조건(같은 이름·내용으로 내려받기, 크기·형식 거절, 조회자 권한, 삭제와 이력)을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class FileApiTest {

    /** PDF 파일 서명 (%PDF) */
    private static final byte[] PDF = "%PDF-1.7\n".getBytes();
    /** HWP·DOC·XLS·PPT 공통 파일 서명 (OLE) */
    private static final byte[] OLE = HexFormat.of().parseHex("D0CF11E0A1B11AE1");
    /** 윈도우 실행 파일 서명 (MZ) */
    private static final byte[] EXE = "MZ".getBytes();

    @Autowired
    MockMvc mvc;

    /** 완료 조건: 올린 파일을 같은 이름·내용으로 내려받을 수 있다. 조회자도 내려받을 수 있다. */
    @Test
    void 올린_파일을_같은_이름과_내용으로_내려받을_수_있다() throws Exception {
        byte[] body = fileOf(PDF, 5_000);
        long id = upload(new MockMultipartFile("file", "샘플 계약서 (2026).pdf", "application/pdf", body), "manager1");

        mvc.perform(get("/api/files/" + id).with(login("viewer", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("샘플 계약서 (2026).pdf"))
                .andExpect(jsonPath("$.size").value(body.length))
                .andExpect(jsonPath("$.createdBy").value("manager1"));
        mvc.perform(get("/api/files/" + id + "/download").with(login("viewer", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''%EC%83%98%ED%94%8C%20%EA%B3%84%EC%95%BD%EC%84%9C%20%282026%29.pdf")))
                .andExpect(content().bytes(body));
    }

    /** HWP는 브라우저가 형식을 비워 보내도 확장자·서명으로 판단해 받고, 한글 문서 형식으로 저장한다. */
    @Test
    void HWP_파일은_브라우저가_보낸_형식과_관계없이_올릴_수_있다() throws Exception {
        long id = upload(new MockMultipartFile("file", "납품계약서.hwp", "application/octet-stream", fileOf(OLE, 100)),
                "manager1");

        mvc.perform(get("/api/files/" + id).with(login("viewer", "VIEWER")))
                .andExpect(jsonPath("$.contentType").value("application/x-hwp"));
    }

    /** 브라우저가 C:\fakepath\... 처럼 경로를 붙여 보내도 파일 이름만 저장한다. */
    @Test
    void 경로가_붙은_파일_이름은_이름만_저장한다() throws Exception {
        long id = upload(new MockMultipartFile("file", "C:\\fakepath\\인증서.pdf", "application/pdf", fileOf(PDF, 10)),
                "manager1");

        mvc.perform(get("/api/files/" + id).with(login("viewer", "VIEWER")))
                .andExpect(jsonPath("$.originalName").value("인증서.pdf"));
    }

    /** 완료 조건: 허용하지 않은 형식은 안내와 함께 거절한다. */
    @Test
    void 허용하지_않은_형식은_안내와_함께_거절한다() throws Exception {
        for (String name : new String[]{"setup.exe", "메모.txt", "확장자없음"}) {
            mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", name, null, fileOf(EXE, 10)))
                            .with(login("manager1", "MANAGER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("file"))
                    .andExpect(jsonPath("$.detail").value(FileType.ALLOWED_DESCRIPTION));
        }
    }

    /** 확장자만 pdf로 바꾼 실행 파일처럼, 내용이 확장자와 다르면 거절한다. */
    @Test
    void 확장자와_내용이_다르면_거절한다() throws Exception {
        mvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "계약서.pdf", "application/pdf", fileOf(EXE, 100)))
                        .with(login("manager1", "MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("확장자(PDF)와 맞지 않습니다")));
    }

    /** 완료 조건: 한도(20MB)를 넘는 파일은 안내와 함께 거절하고, 딱 한도인 파일은 받는다. */
    @Test
    void 크기_한도를_넘으면_거절하고_한도까지는_받는다() throws Exception {
        int limit = 20 * 1024 * 1024;
        upload(new MockMultipartFile("file", "한도.pdf", "application/pdf", fileOf(PDF, limit)), "manager1");

        mvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "초과.pdf", "application/pdf", fileOf(PDF, limit + 1)))
                        .with(login("manager1", "MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.detail").value(containsString("20MB 이하의 파일만 올릴 수 있습니다")));
    }

    /** 빈 파일은 거절한다. */
    @Test
    void 빈_파일은_거절한다() throws Exception {
        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "빈파일.pdf", "application/pdf", new byte[0]))
                        .with(login("manager1", "MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("올릴 파일을 선택해 주세요."));
    }

    /** 완료 조건: 조회자는 업로드·삭제할 수 없다. */
    @Test
    void 조회자는_올리거나_삭제할_수_없다() throws Exception {
        long id = upload(new MockMultipartFile("file", "a.pdf", "application/pdf", fileOf(PDF, 10)), "manager1");

        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "b.pdf", "application/pdf", fileOf(PDF, 10)))
                        .with(login("viewer", "VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/files/" + id).with(login("viewer", "VIEWER")))
                .andExpect(status().isForbidden());
    }

    /** 완료 조건: 삭제한 파일은 보이지 않고, 누가 언제 지웠는지 이력이 남는다. */
    @Test
    void 삭제한_파일은_보이지_않고_이력이_남는다() throws Exception {
        long id = upload(new MockMultipartFile("file", "지울파일.pdf", "application/pdf", fileOf(PDF, 10)), "manager1");

        mvc.perform(delete("/api/files/" + id).with(login("manager2", "MANAGER")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/files/" + id).with(login("viewer", "VIEWER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/files/" + id + "/download").with(login("viewer", "VIEWER"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/files/" + id).with(login("manager1", "MANAGER"))).andExpect(status().isNotFound());

        String row = "$.content[?(@.id == %d)]".formatted(id);
        mvc.perform(get("/api/admin/files/deleted").with(login("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row + ".originalName").value(hasItem("지울파일.pdf")))
                .andExpect(jsonPath(row + ".createdBy").value(hasItem("manager1")))
                .andExpect(jsonPath(row + ".deletedBy").value(hasItem("manager2")));
    }

    /** 삭제 이력은 관리자만 볼 수 있다. */
    @Test
    void 삭제_이력은_관리자만_볼_수_있다() throws Exception {
        mvc.perform(get("/api/admin/files/deleted").with(login("manager1", "MANAGER")))
                .andExpect(status().isForbidden());
    }

    /** 로그인하지 않으면 파일을 내려받을 수 없다 (NFR-006). */
    @Test
    void 로그인하지_않으면_401() throws Exception {
        long id = upload(new MockMultipartFile("file", "a.pdf", "application/pdf", fileOf(PDF, 10)), "manager1");

        mvc.perform(get("/api/files/" + id + "/download")).andExpect(status().isUnauthorized());
    }

    /** 지정한 사용자로 파일을 올리고, 성공(201)을 확인한 뒤 발급된 ID를 돌려준다. */
    private long upload(MockMultipartFile file, String username) throws Exception {
        String body = mvc.perform(multipart("/api/files").file(file).with(login(username, "MANAGER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    /** 지정한 아이디·역할로 로그인한 요청을 만든다. */
    private static RequestPostProcessor login(String username, String role) {
        return user(username).roles(role);
    }

    /** 서명으로 시작하고 나머지를 임의 값으로 채운, 지정한 크기의 파일 내용을 만든다. */
    private static byte[] fileOf(byte[] signature, int size) {
        byte[] bytes = Arrays.copyOf(signature, size);
        for (int i = signature.length; i < size; i++) {
            bytes[i] = (byte) (i * 31);
        }
        return bytes;
    }
}
