package com.hankooknongsan.supplier.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.common.RenewalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 계약 API의 요청·응답 형식 모음 */
public final class ContractDtos {

    /** DTO 모음 클래스이므로 생성을 막는다. */
    private ContractDtos() {
    }

    /** 계약 등록·수정·갱신 요청. 종료일이 비어 있으면 무기한 계약, 자동연장을 비우면 "아니오"로 본다. */
    public record ContractRequest(
            @NotNull(message = "협력업체를 선택해 주세요.") Long supplierId,
            @NotBlank(message = "계약번호를 입력해 주세요.") @Size(max = 50) String contractNumber,
            @NotBlank(message = "계약명을 입력해 주세요.") @Size(max = 200) String title,
            @NotNull(message = "시작일을 입력해 주세요.") LocalDate startDate,
            LocalDate endDate,
            Boolean autoRenew,
            Long ownerId,
            @Size(max = 500) String documentUrl,
            @Size(max = 1000) String memo) {
    }

    /** 계약 목록·상세 응답 */
    public record ContractItem(Long id, Long supplierId, String supplierName, String contractNumber, String title,
                               LocalDate startDate, LocalDate endDate, boolean autoRenew,
                               RenewalStatus renewalStatus, Long ownerId, String ownerName, boolean active,
                               Long prevId, String documentUrl, String memo, LocalDateTime createdAt,
                               String createdBy, LocalDateTime updatedAt, String updatedBy) {

        /** 계약 엔티티를 응답 형식으로 변환한다. */
        static ContractItem of(Contract c) {
            AppUser owner = c.getOwner();
            return new ContractItem(c.getId(), c.getSupplier().getId(), c.getSupplier().getName(),
                    c.getContractNumber(), c.getTitle(), c.getStartDate(), c.getEndDate(), c.isAutoRenew(),
                    c.getRenewalStatus(), owner == null ? null : owner.getId(), owner == null ? null : owner.getName(),
                    c.isActive(), c.getPrev() == null ? null : c.getPrev().getId(), c.getDocumentUrl(), c.getMemo(),
                    c.getCreatedAt(), c.getCreatedBy(), c.getUpdatedAt(), c.getUpdatedBy());
        }
    }

    /**
     * 저장 결과. 저장은 됐지만 사용자가 알아야 할 내용(예: 계약번호 중복)이 있으면 warnings에 담는다.
     */
    public record Saved(ContractItem item, List<String> warnings) {
    }
}
