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
        public static FileInfo of(StoredFile f) {
            return new FileInfo(f.getId(), f.getOriginalName(), f.getContentType(), f.getSize(),
                    f.getCreatedAt(), f.getCreatedBy());
        }
    }

    /** 업체 자료 목록의 한 행. 일반 자료는 계약·인증 정보가 null이고, 종류를 붙이지 않았으면 종류도 null이다. */
    public record SupplierFile(FileInfo file, Long contractId, String contractName,
                               Long certificationId, String certificationName,
                               Long categoryId, String categoryName) {
        /** 파일 정보와 현재 연결된 계약·인증 이름을 함께 보여준다. */
        static SupplierFile of(StoredFile f) {
            var c = f.getContract();
            var cert = f.getCertification();
            return new SupplierFile(FileInfo.of(f), c == null ? null : c.getId(),
                    c == null ? null : (c.getContractNumber().isEmpty() ? "" : c.getContractNumber() + " · ") + c.getTitle(),
                    cert == null ? null : cert.getId(),
                    cert == null ? null : cert.getDisplayName() + " · " + cert.getCertificateNumber(),
                    f.getCategory() == null ? null : f.getCategory().getId(),
                    f.getCategory() == null ? null : f.getCategory().getName());
        }
    }

    /**
     * 전체 자료 검색 결과의 한 행 (#53). 파일이 어디에 있는지와, 그곳으로 이동하는 데 필요한 ID를 함께 준다.
     * location은 "업체 자료"·"계약 첨부"·"인증 첨부"·"양식 자료실" 중 하나다. 해당하지 않는 항목은 null이다.
     */
    public record FileSearchItem(FileInfo file, String location, Long supplierId, String supplierName,
                                 String contractName, String certificationName, String categoryName,
                                 Long formId, String formTitle, Long folderId) {
        /** 검색 결과 한 행을 응답 형식으로 바꾼다. 업체는 파일이 직접 또는 계약·인증을 거쳐 속한 업체다. */
        static FileSearchItem of(StoredFileRepository.FileHit hit) {
            StoredFile f = hit.getFile();
            SupplierFile linked = SupplierFile.of(f);
            var supplier = f.getContract() != null ? f.getContract().getSupplier()
                    : f.getCertification() != null ? f.getCertification().getSupplier() : f.getSupplier();
            String location = f.getContract() != null ? "계약 첨부" : f.getCertification() != null ? "인증 첨부"
                    : supplier != null ? "업체 자료" : "양식 자료실";
            return new FileSearchItem(linked.file(), location, supplier == null ? null : supplier.getId(),
                    supplier == null ? null : supplier.getName(), linked.contractName(), linked.certificationName(),
                    linked.categoryName(),
                    hit.getFormId(), hit.getFormTitle(), hit.getFolderId());
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
