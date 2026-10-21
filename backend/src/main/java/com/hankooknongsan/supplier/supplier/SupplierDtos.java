package com.hankooknongsan.supplier.supplier;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 협력업체 API의 요청·응답 형식 모음 */
public final class SupplierDtos {

    /** DTO 모음 클래스이므로 생성을 막는다. */
    private SupplierDtos() {
    }

    /** 협력업체 등록·수정 요청. 필수값·형식 검증 규칙을 함께 정의한다. */
    public record SupplierRequest(
            @NotBlank(message = "업체명을 입력해 주세요.") @Size(max = 100) String name,
            @Pattern(regexp = "^$|^\\d{3}-\\d{2}-\\d{5}$", message = "사업자번호는 000-00-00000 형식입니다.")
            String businessNumber,
            @NotNull(message = "거래상태를 선택해 주세요.") SupplierStatus status,
            @Size(max = 50) String contactName,
            @Size(max = 30) String contactPhone,
            @Email(message = "이메일 형식이 올바르지 않습니다.") String contactEmail,
            Long ownerId,
            @Size(max = 1000) String memo) {
    }

    /** 협력업체 목록 그리드의 한 행. expiringCount는 만료됐거나 만료예정 기간(기본 90일) 안에 만료되는 계약·인증 수 */
    public record SupplierListItem(Long id, String code, String name, SupplierStatus status, String contactName,
                                   Long ownerId, String ownerName, long contractCount, long certCount,
                                   long expiringCount) {
    }

    /** 협력업체 상세 화면 데이터 (등록·수정 이력 포함) */
    public record SupplierDetail(Long id, String code, String name, String businessNumber, SupplierStatus status,
                                 String contactName, String contactPhone, String contactEmail, Long ownerId,
                                 String ownerName, String memo, long contractCount, long certCount,
                                 long expiringCount, LocalDateTime createdAt, String createdBy,
                                 LocalDateTime updatedAt, String updatedBy) {
    }

    /**
     * 사업자번호 확인 결과.
     *
     * @param valid      검증번호가 맞는지
     * @param duplicates 같은 사업자번호로 이미 등록된 업체 (수정 중인 자기 자신은 제외)
     */
    public record BusinessNumberCheck(boolean valid, List<DuplicateSupplier> duplicates) {
    }

    /** 사업자번호가 같은 기존 업체 요약 */
    public record DuplicateSupplier(Long id, String code, String name, SupplierStatus status) {
    }
}
