package com.hankooknongsan.supplier.file;

import java.time.LocalDateTime;

import org.springframework.core.io.Resource;

/** 첨부파일 API의 요청·응답 형식 모음 */
public final class FileDtos {

    /** DTO 모음 클래스이므로 생성을 막는다. */
    private FileDtos() {
    }

    /** 첨부파일 정보. size는 바이트, createdBy는 올린 사람 아이디 */
    public record FileInfo(Long id, String originalName, String contentType, long size,
                           LocalDateTime createdAt, String createdBy) {

        /** 엔티티를 응답 형식으로 바꾼다. */
        static FileInfo of(StoredFile f) {
            return new FileInfo(f.getId(), f.getOriginalName(), f.getContentType(), f.getSize(),
                    f.getCreatedAt(), f.getCreatedBy());
        }
    }

    /** 삭제 이력의 한 행. 언제 누가 올렸고 언제 누가 지웠는지 보여준다. */
    public record DeletedFile(Long id, String originalName, long size, LocalDateTime createdAt, String createdBy,
                              LocalDateTime deletedAt, String deletedBy) {

        /** 엔티티를 응답 형식으로 바꾼다. */
        static DeletedFile of(StoredFile f) {
            return new DeletedFile(f.getId(), f.getOriginalName(), f.getSize(), f.getCreatedAt(), f.getCreatedBy(),
                    f.getDeletedAt(), f.getDeletedBy());
        }
    }

    /** 내려받을 파일의 정보와 내용 */
    public record Download(FileInfo info, Resource content) {
    }
}
