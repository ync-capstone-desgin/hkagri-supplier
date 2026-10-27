package com.hankooknongsan.supplier.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.UUID;

import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.file.FileDtos.DeletedFile;
import com.hankooknongsan.supplier.file.FileDtos.Download;
import com.hankooknongsan.supplier.file.FileDtos.FileInfo;
import com.hankooknongsan.supplier.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 올리기·내려받기 업무 서비스 (FR-021, FR-022 공통).
 * 업체 자료 탭, 계약·인증 첨부, 양식 자료실이 모두 이 서비스로 파일을 다룬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    /** 파일 이름 최대 길이 (DB 컬럼 길이) */
    private static final int MAX_NAME_LENGTH = 255;

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final FilePolicy policy;
    private final AppClock clock;

    /**
     * 파일을 올린다.
     * 크기·형식 규칙({@link FilePolicy})을 먼저 확인하고, 형식은 브라우저가 알려준 값 대신 확인한 형식으로 기록한다.
     * 파일 정보를 DB에 먼저 기록한 뒤 저장소에 내용을 쓴다. 저장소에 쓰다 실패하면 DB 기록도 함께 취소된다.
     * 반대로 저장소에 쓴 뒤 DB 반영·커밋이 실패하면, 롤백되는 시점에 저장한 파일을 지워 주인 없는 파일이 남지 않게 한다.
     */
    @Transactional
    public FileInfo upload(MultipartFile file) {
        String name = cleanName(file == null ? null : file.getOriginalFilename());
        FileType type = policy.check(file, name);
        StoredFile saved = files.save(new StoredFile(name, type.contentType(), file.getSize(),
                newStorageKey(clock.today())));
        try (InputStream in = file.getInputStream()) {
            storage.save(saved.getStorageKey(), in);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드한 파일을 읽지 못했습니다.", e);
        }
        deleteOnRollback(saved.getStorageKey());
        files.flush();
        return FileInfo.of(saved);
    }

    /**
     * 파일을 삭제한다. 기록과 저장소의 내용은 남겨 두고 삭제한 사람·시각만 기록한다 (NFR-007).
     * 삭제한 파일은 조회·내려받기에서 없는 파일(404)로 보인다. 이미 삭제했거나 없으면 404.
     */
    @Transactional
    public void delete(Long id, String deletedBy) {
        if (files.markDeleted(id, deletedBy, clock.now()) == 0) {
            throw BusinessException.notFound("파일");
        }
    }

    /** 삭제한 파일 목록(삭제 이력)을 최근에 지운 순으로 조회한다. (관리자용) */
    public PageResponse<DeletedFile> deletedHistory(Pageable pageable) {
        return PageResponse.of(files.findByDeletedAtIsNotNullOrderByDeletedAtDesc(pageable), DeletedFile::of);
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

    /**
     * 현재 트랜잭션이 롤백되면 저장소에서 해당 파일을 지우도록 예약한다.
     * 정리하다 실패해도 원래 오류를 가리지 않도록 경고만 남긴다.
     */
    private void deleteOnRollback(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    storage.delete(storageKey);
                } catch (RuntimeException e) {
                    log.warn("취소된 업로드의 파일을 지우지 못했습니다: {}", storageKey, e);
                }
            }
        });
    }

    /** ID로 삭제하지 않은 첨부파일을 찾는다. 없거나 삭제했으면 404. */
    private StoredFile find(Long id) {
        return files.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> BusinessException.notFound("파일"));
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
}
