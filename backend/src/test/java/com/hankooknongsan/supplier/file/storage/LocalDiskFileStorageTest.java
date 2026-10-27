package com.hankooknongsan.supplier.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 서버 디스크 저장소 테스트.
 * 저장한 내용을 그대로 읽을 수 있는지, 저장 폴더 밖이나 이미 있는 위치에는 쓰지 않는지 확인한다.
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

    /** 이미 있는 키에는 덮어쓰지 않는다. */
    @Test
    void 이미_있는_키에는_덮어쓰지_않는다() {
        LocalDiskFileStorage storage = new LocalDiskFileStorage(dir.toString());
        storage.save("same", new ByteArrayInputStream(new byte[]{1}));

        assertThatThrownBy(() -> storage.save("same", new ByteArrayInputStream(new byte[]{2})))
                .isInstanceOf(UncheckedIOException.class);
    }
}
