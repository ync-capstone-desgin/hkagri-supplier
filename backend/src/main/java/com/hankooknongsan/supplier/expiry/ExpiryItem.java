package com.hankooknongsan.supplier.expiry;

import java.time.LocalDate;

import com.hankooknongsan.supplier.common.ExpiryStatus;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.supplier.SupplierStatus;

/**
 * 만료 예정 목록의 한 행. 계약과 인증을 같은 형식으로 합쳐 보여준다.
 *
 * @param targetType     대상 종류 (계약/인증)
 * @param targetId       계약 또는 인증 ID
 * @param name           대상명 (계약명 또는 인증 종류 이름)
 * @param number         계약번호 또는 인증번호
 * @param expiryDate     만료일 (계약은 종료일)
 * @param daysLeft       남은 일수 (오늘 만료 0, 지남 음수, 만료일 없음 null)
 * @param expiryStatus   날짜로 계산한 만료상태
 * @param renewalStatus  담당자가 기록한 갱신 처리상태
 */
public record ExpiryItem(TargetType targetType, Long targetId, Long supplierId, String supplierName,
                         SupplierStatus supplierStatus, String name, String number, LocalDate expiryDate,
                         Integer daysLeft, ExpiryStatus expiryStatus, RenewalStatus renewalStatus, Long ownerId,
                         String ownerName) {

    /** 목록 행을 식별하는 키 (계약·인증 ID가 겹칠 수 있으므로 종류를 붙인다). 예: CONTRACT-12 */
    public String key() {
        return targetType + "-" + targetId;
    }
}
