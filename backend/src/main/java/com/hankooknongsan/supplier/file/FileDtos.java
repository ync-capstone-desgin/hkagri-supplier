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

    /** 업체 자료 목록의 한 행. 일반 자료는 계약·인증 정보가 null이다. */
    public record SupplierFile(FileInfo file, Long contractId, String contractName,
                               Long certificationId, String certificationName) {
        /** 파일 정보와 현재 연결된 계약·인증 이름을 함께 보여준다. */
        static SupplierFile of(StoredFile f) {
            var c = f.getContract();
            var cert = f.getCertification();
            return new SupplierFile(FileInfo.of(f), c == null ? null : c.getId(),
                    c == null ? null : c.getContractNumber() + " · " + c.getTitle(),
                    cert == null ? null : cert.getId(),
                    cert == null ? null : cert.getCertType().getName() + " · " + cert.getCertificateNumber());
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
