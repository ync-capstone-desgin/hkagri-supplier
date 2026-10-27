package com.hankooknongsan.supplier.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.UUID;

import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.file.FileDtos.Download;
import com.hankooknongsan.supplier.file.FileDtos.FileInfo;
import com.hankooknongsan.supplier.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 올리기·내려받기 업무 서비스 (FR-021, FR-022 공통).
 * 업체 자료 탭, 계약·인증 첨부, 양식 자료실이 모두 이 서비스로 파일을 다룬다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    /** 파일 이름 최대 길이 (DB 컬럼 길이) */
    private static final int MAX_NAME_LENGTH = 255;

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final AppClock clock;

    /**
     * 파일을 올린다.
     * 파일 정보를 DB에 먼저 기록한 뒤 저장소에 내용을 쓴다. 저장소에 쓰다 실패하면 DB 기록도 함께 취소된다.
     * 빈 파일이면 400.
     */
    @Transactional
    public FileInfo upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.invalid("file", "올릴 파일을 선택해 주세요.");
        }
        StoredFile saved = files.save(new StoredFile(cleanName(file.getOriginalFilename()), contentTypeOf(file),
                file.getSize(), newStorageKey(clock.today())));
        try (InputStream in = file.getInputStream()) {
            storage.save(saved.getStorageKey(), in);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드한 파일을 읽지 못했습니다.", e);
        }
        files.flush();
        return FileInfo.of(saved);
    }

    /** 파일 정보를 조회한다. 없으면 404. */
    public FileInfo get(Long id) {
        return FileInfo.of(find(id));
    }

    /** 내려받을 파일의 정보와 내용을 함께 돌려준다. 없으면 404. */
    public Download download(Long id) {
        StoredFile f = find(id);
        return new Download(FileInfo.of(f), storage.load(f.getStorageKey()));
    }

    /** ID로 첨부파일을 찾는다. 없으면 404. */
    private StoredFile find(Long id) {
        return files.findById(id).orElseThrow(() -> BusinessException.notFound("파일"));
    }

    /**
     * 저장소 안의 위치를 새로 만든다. 예: 2026/10/3f2a...-....
     * 원래 이름을 쓰지 않으므로 같은 이름의 파일이 겹치거나 이름으로 경로를 조작할 수 없다.
     */
    static String newStorageKey(LocalDate today) {
        return "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID());
    }

    /**
     * 브라우저가 보낸 파일 이름에서 폴더 경로를 떼고 이름만 남긴다. (일부 브라우저는 C:\...\a.pdf 처럼 전체 경로를 보낸다)
     * 이름이 없으면 "이름 없는 파일", 너무 길면 확장자를 살려 뒤에서 자른다.
     */
    static String cleanName(String original) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(original == null ? "" : original));
        if (!StringUtils.hasText(name)) {
            return "이름 없는 파일";
        }
        name = name.strip();
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        String ext = StringUtils.getFilenameExtension(name);
        String suffix = ext == null || ext.length() > 20 ? "" : "." + ext;
        return name.substring(0, MAX_NAME_LENGTH - suffix.length()) + suffix;
    }

    /** 브라우저가 알려준 파일 형식을 쓰고, 없으면 일반 바이너리로 기록한다. */
    private static String contentTypeOf(MultipartFile file) {
        String type = file.getContentType();
        return StringUtils.hasText(type) ? type : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
