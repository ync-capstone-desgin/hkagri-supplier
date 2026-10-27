package com.hankooknongsan.supplier.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 첨부파일 서비스 테스트 (#26, #24 리뷰 반영).
 * 업로드가 실패하거나 취소됐을 때 주인 없는 파일이 남지 않는지, 동시에 삭제해도 처음 삭제 기록이 유지되는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class FileServiceTest {

    /** PDF 파일 서명 (%PDF) */
    private static final byte[] PDF = "%PDF-1.7\n".getBytes();

    @Autowired
    FileService service;
    @Autowired
    StoredFileRepository files;
    @Autowired
    TransactionTemplate tx;

    @Value("${app.file.dir}")
    Path fileDir;

    /** 올리는 도중 읽기 오류가 나면 DB 기록도, 반쯤 쓴 파일도 남지 않는다. */
    @Test
    void 저장_중_오류가_나면_DB와_디스크에_아무것도_남지_않는다() throws Exception {
        Set<Path> before = storedFiles();
        long rowsBefore = files.count();

        assertThatThrownBy(() -> service.upload(new FailingFile("끊긴파일.pdf", 8 * 1024)))
                .isInstanceOf(RuntimeException.class);

        assertThat(storedFiles()).isEqualTo(before);
        assertThat(files.count()).isEqualTo(rowsBefore);
    }

    /** 디스크에 저장한 뒤 트랜잭션이 롤백되면(DB 반영·커밋 실패 등) 저장한 파일을 지운다. */
    @Test
    void 저장_후_롤백되면_저장한_파일을_지운다() throws Exception {
        Set<Path> before = storedFiles();

        tx.executeWithoutResult(status -> {
            service.upload(new MockMultipartFile("file", "취소될파일.pdf", "application/pdf", pdfOf(1000)));
            assertThat(storedFiles()).hasSize(before.size() + 1);
            status.setRollbackOnly();
        });

        assertThat(storedFiles()).isEqualTo(before);
    }

    /** 두 사람이 동시에 지워도 한 명만 성공하고, 나머지는 404를 받으며 처음 삭제한 사람이 기록에 남는다. */
    @Test
    void 동시에_삭제하면_한_명만_성공하고_처음_기록이_남는다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                long id = service.upload(new MockMultipartFile("file", "동시삭제.pdf", "application/pdf", pdfOf(100))).id();
                CyclicBarrier start = new CyclicBarrier(2);
                List<Future<String>> results = new ArrayList<>();
                for (String user : List.of("manager1", "manager2")) {
                    results.add(pool.submit(() -> {
                        start.await();
                        try {
                            service.delete(id, user);
                            return user;
                        } catch (BusinessException e) {
                            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                            return null;
                        }
                    }));
                }
                List<String> winners = new ArrayList<>();
                for (Future<String> r : results) {
                    String winner = r.get();
                    if (winner != null) {
                        winners.add(winner);
                    }
                }

                assertThat(winners).as("%d회차 성공한 요청 수", round).hasSize(1);
                assertThat(files.findById(id).orElseThrow().getDeletedBy()).isEqualTo(winners.getFirst());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** 테스트용 저장 폴더 아래의 파일 목록을 돌려준다. */
    private Set<Path> storedFiles() {
        if (!Files.exists(fileDir)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.walk(fileDir)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF 서명으로 시작하는 지정한 크기의 파일 내용을 만든다. */
    private static byte[] pdfOf(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(PDF, 0, bytes, 0, PDF.length);
        return bytes;
    }

    /**
     * 읽는 도중 오류가 나는 업로드 파일.
     * 앞부분(서명 포함)은 정상으로 주고, failAfter 바이트를 넘게 읽으면 연결이 끊긴 것처럼 IOException을 던진다.
     */
    static class FailingFile extends MockMultipartFile {

        private final byte[] content;
        private final int failAfter;

        /** 크기는 failAfter보다 크게 알려 주어, 크기 검사는 통과하고 저장 도중에 실패하게 만든다. */
        FailingFile(String name, int failAfter) {
            this(name, pdfOf(failAfter * 2), failAfter);
        }

        /** 파일 내용과 실패 지점을 지정해 만든다. */
        private FailingFile(String name, byte[] content, int failAfter) {
            super("file", name, "application/pdf", content);
            this.content = content;
            this.failAfter = failAfter;
        }

        /** 정해진 바이트까지만 읽히고 그 뒤에는 오류를 내는 스트림을 돌려준다. */
        @Override
        public InputStream getInputStream() {
            return new InputStream() {
                private int pos;

                /** 한 바이트를 읽는다. failAfter를 넘으면 오류를 던진다. */
                @Override
                public int read() throws IOException {
                    if (pos >= failAfter) {
                        throw new IOException("업로드 연결이 끊겼습니다 (테스트)");
                    }
                    return content[pos++] & 0xFF;
                }
            };
        }
    }
}
