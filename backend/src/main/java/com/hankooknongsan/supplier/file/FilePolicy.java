package com.hankooknongsan.supplier.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import com.hankooknongsan.supplier.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * 올릴 수 있는 파일인지 판단하는 규칙 (NFR-006).
 * 크기 한도(app.file.max-size, 기본 20MB)와 허용 형식({@link FileType})을 확인한다.
 */
@Component
public class FilePolicy {

    private final DataSize maxSize;

    /** 설정된 파일 크기 한도로 규칙을 만든다. */
    public FilePolicy(@Value("${app.file.max-size}") DataSize maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * 올린 파일이 규칙에 맞는지 확인하고, 맞으면 파일 형식을 돌려준다.
     * 빈 파일, 한도를 넘는 크기, 허용하지 않는 확장자, 확장자와 내용이 다른 파일(예: exe를 pdf로 이름만 바꾼 것)은 400으로 거절한다.
     */
    public FileType check(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.invalid("file", "올릴 파일을 선택해 주세요.");
        }
        if (file.getSize() > maxSize.toBytes()) {
            throw BusinessException.invalid("file", "%s 이하의 파일만 올릴 수 있습니다. (선택한 파일: %s)"
                    .formatted(format(maxSize.toBytes()), format(file.getSize())));
        }
        FileType type = FileType.ofFileName(fileName)
                .orElseThrow(() -> BusinessException.invalid("file", FileType.ALLOWED_DESCRIPTION));
        if (!type.matches(head(file))) {
            throw BusinessException.invalid("file", "파일 내용이 확장자(%s)와 맞지 않습니다. 파일이 손상되었거나 다른 형식일 수 있습니다."
                    .formatted(type.name()));
        }
        return type;
    }

    /** 화면 안내에 쓰는 크기 한도 문구를 반환한다. 예: "20MB 이하의 파일만 올릴 수 있습니다." */
    public String maxSizeMessage() {
        return "%s 이하의 파일만 올릴 수 있습니다.".formatted(format(maxSize.toBytes()));
    }

    /** 서명 확인용으로 파일 앞부분만 읽는다. */
    private static byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(FileType.signatureLength());
        } catch (IOException e) {
            throw new UncheckedIOException("업로드한 파일을 읽지 못했습니다.", e);
        }
    }

    /** 바이트 수를 사람이 읽기 쉬운 단위로 바꾼다. 예: 20971520 → "20MB", 1572864 → "1.5MB" */
    static String format(long bytes) {
        if (bytes < 1024 * 1024) {
            return "%.0fKB".formatted(Math.max(1, Math.ceil(bytes / 1024.0)));
        }
        double mb = bytes / (1024.0 * 1024);
        return mb == Math.floor(mb) ? "%.0fMB".formatted(mb) : "%.1fMB".formatted(mb);
    }
}
