package com.hankooknongsan.supplier.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 서버 디스크에 파일을 보관하는 저장소 (app.file.storage=local, 기본값).
 * 저장 폴더는 app.file.dir(환경변수 FILE_DIR)로 정하며, 운영에서는 Docker 볼륨을 연결해 컨테이너가 바뀌어도 파일이 남게 한다.
 */
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

    /** 저장 키 경로에 파일을 새로 쓴다. 중간 폴더는 자동으로 만든다. */
    @Override
    public void save(String key, InputStream content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 저장하지 못했습니다: " + key, e);
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
