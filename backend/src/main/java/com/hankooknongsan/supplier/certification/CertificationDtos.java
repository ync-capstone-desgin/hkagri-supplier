package com.hankooknongsan.supplier.certification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.common.RenewalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 인증 API의 요청·응답 형식 모음 */
public final class CertificationDtos {

    /** DTO 모음 클래스이므로 생성을 막는다. */
    private CertificationDtos() {
    }

    /** 인증 등록·수정·갱신 요청. 만료일이 비어 있으면 "확인 필요" 대상이 된다. */
    public record CertificationRequest(
            @NotNull(message = "협력업체를 선택해 주세요.") Long supplierId,
            @NotBlank(message = "인증 종류를 선택해 주세요.") String certTypeCode,
            @NotBlank(message = "인증번호를 입력해 주세요.") @Size(max = 50) String certificateNumber,
            @NotNull(message = "발급일을 입력해 주세요.") LocalDate issuedDate,
            LocalDate expiryDate,
            Long ownerId,
            @Size(max = 500) String documentUrl,
            @Size(max = 1000) String memo) {
    }

    /** 인증 종류 선택상자용 */
    public record CertTypeItem(String code, String name) {
    }

    /** 인증 목록·상세 응답 */
    public record CertificationItem(Long id, Long supplierId, String supplierName, String certTypeCode,
                                    String certTypeName, String certificateNumber, LocalDate issuedDate,
                                    LocalDate expiryDate, RenewalStatus renewalStatus, Long ownerId,
                                    String ownerName, boolean active, Long prevId, String documentUrl, String memo,
                                    LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt,
                                    String updatedBy) {

        /** 인증 엔티티를 응답 형식으로 변환한다. */
        static CertificationItem of(Certification x) {
            AppUser owner = x.getOwner();
            return new CertificationItem(x.getId(), x.getSupplier().getId(), x.getSupplier().getName(),
                    x.getCertType().getCode(), x.getCertType().getName(), x.getCertificateNumber(),
                    x.getIssuedDate(), x.getExpiryDate(), x.getRenewalStatus(),
                    owner == null ? null : owner.getId(), owner == null ? null : owner.getName(), x.isActive(),
                    x.getPrev() == null ? null : x.getPrev().getId(), x.getDocumentUrl(), x.getMemo(),
                    x.getCreatedAt(), x.getCreatedBy(), x.getUpdatedAt(), x.getUpdatedBy());
        }
    }

    /** 저장 결과. 저장은 됐지만 사용자가 알아야 할 내용(예: 인증번호 중복)이 있으면 warnings에 담는다. */
    public record Saved(CertificationItem item, List<String> warnings) {
    }
}
