package com.hankooknongsan.supplier.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 서버 디스크 저장소 테스트.
 * 저장한 내용을 그대로 읽을 수 있는지, 저장 폴더 밖이나 이미 있는 위치에는 쓰지 않는지,
 * 쓰다 실패하면 반쪽 파일을 남기지 않는지 확인한다.
 */
class LocalDiskFileStorageTest {

    @TempDir
    Path dir;

    /** 저장한 내용을 같은 키로 그대로 읽을 수 있다. 중간 폴더는 자동으로 만든다. */
    @Test
    void 저장한_내용을_그대로_읽을_수_있다() throws Exception {
        LocalDiskFileStorage storage = new LocalDiskFileStorage(dir.toString());

        storage.save("2026/10/abc", new ByteArrayInputStream("내용".getBytes()));

        assertThat(storage.load("2026/10/abc").getContentAsByteArray()).isEqualTo("내용".getBytes());
    }

    /** "../" 등으로 저장 폴더 밖을 가리키는 키는 거절한다. */
    @Test
    void 저장_폴더_밖을_가리키는_키는_거절한다() {
        LocalDiskFileStorage storage = new LocalDiskFileStorage(dir.resolve("files").toString());

        assertThatThrownBy(() -> storage.save("../outside", new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.load("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 이미 있는 키에는 덮어쓰지 않고, 실패해도 원래 파일은 지우지 않는다. */
    @Test
    void 이미_있는_키에는_덮어쓰지_않는다() throws Exception {
        LocalDiskFileStorage storage = new LocalDiskFileStorage(dir.toString());
        storage.save("same", new ByteArrayInputStream(new byte[]{1}));

        assertThatThrownBy(() -> storage.save("same", new ByteArrayInputStream(new byte[]{2})))
                .isInstanceOf(UncheckedIOException.class);
        assertThat(storage.load("same").getContentAsByteArray()).isEqualTo(new byte[]{1});
    }

    /** 쓰는 도중 읽기 오류가 나면 이번에 만든 반쪽 파일을 남기지 않는다. (#24 리뷰) */
    @Test
    void 쓰는_도중_오류가_나면_만든_파일을_지운다() {
        LocalDiskFileStorage storage = new LocalDiskFileStorage(dir.toString());
        InputStream broken = new SequenceInputStream(new ByteArrayInputStream(new byte[8 * 1024]), new InputStream() {
            /** 앞의 8KB를 다 읽으면 연결이 끊긴 것처럼 오류를 던진다. */
            @Override
            public int read() throws IOException {
                throw new IOException("업로드 연결이 끊겼습니다 (테스트)");
            }
        });

        assertThatThrownBy(() -> storage.save("2026/10/broken", broken)).isInstanceOf(UncheckedIOException.class);
        assertThat(dir.resolve("2026/10/broken")).doesNotExist();
    }

    /** 지우면 더 이상 읽을 수 없고, 없는 키를 지워도 오류가 나지 않는다. */
    @Test
    void 지운_파일은_읽을_수_없고_없는_키는_무시한다() {
        LocalDiskFileStorage storage = new LocalDiskFileStorage(dir.toString());
        storage.save("gone", new ByteArrayInputStream(new byte[]{1}));

        storage.delete("gone");
        storage.delete("never-existed");

        assertThat(dir.resolve("gone")).doesNotExist();
        assertThatThrownBy(() -> storage.load("gone")).isInstanceOf(UncheckedIOException.class);
    }
}
