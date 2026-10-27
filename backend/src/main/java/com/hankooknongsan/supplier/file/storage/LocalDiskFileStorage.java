package com.hankooknongsan.supplier.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 서버 디스크에 파일을 보관하는 저장소 (app.file.storage=local, 기본값).
 * 저장 폴더는 app.file.dir(환경변수 FILE_DIR)로 정하며, 운영에서는 Docker 볼륨을 연결해 컨테이너가 바뀌어도 파일이 남게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.file.storage", havingValue = "local", matchIfMissing = true)
public class LocalDiskFileStorage implements FileStorage {

    private final Path root;

    /** 설정된 저장 폴더를 절대 경로로 바꿔 두고, 없으면 만든다. */
    public LocalDiskFileStorage(@Value("${app.file.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 폴더를 만들 수 없습니다: " + root, e);
        }
    }

    /**
     * 저장 키 경로에 파일을 새로 쓴다. 중간 폴더는 자동으로 만든다.
     * 쓰는 도중 읽기·쓰기 오류가 나면 이번에 만든 불완전한 파일을 지운다.
     * 같은 키의 파일이 이미 있어서 실패한 경우에는 남의 파일이므로 지우지 않는다.
     */
    @Override
    public void save(String key, InputStream content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (FileAlreadyExistsException e) {
            throw new UncheckedIOException("같은 위치에 파일이 이미 있습니다: " + key, e);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new UncheckedIOException("파일을 저장하지 못했습니다: " + key, e);
        }
    }

    /** 저장 키 경로의 파일을 지운다. 없으면 아무것도 하지 않는다. */
    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 지우지 못했습니다: " + key, e);
        }
    }

    /** 저장 키 경로의 파일을 리소스로 돌려준다. 파일이 없으면 예외를 던진다. */
    @Override
    public Resource load(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new UncheckedIOException(new IOException("저장된 파일이 없습니다: " + key));
        }
        return new FileSystemResource(target);
    }

    /**
     * 실패 정리용으로 파일을 지운다. 지우다 또 실패해도 원래 오류를 가리지 않도록 경고만 남긴다.
     */
    private static void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("저장에 실패한 파일을 지우지 못했습니다: {}", target, e);
        }
    }

    /**
     * 저장 키를 저장 폴더 안의 실제 경로로 바꾼다.
     * 키에 "../" 등이 섞여 저장 폴더 밖을 가리키면 거절한다.
     */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("저장 폴더 밖의 경로는 사용할 수 없습니다: " + key);
        }
        return target;
    }
}
