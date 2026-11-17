package com.hankooknongsan.supplier.common;

/**
 * 협력업체가 삭제되었음을 알리는 이벤트 (#44). 파일 기능이 받아 그 업체의 일반 자료를 함께 삭제한다.
 *
 * @param supplierId 삭제된 협력업체 ID
 * @param deletedBy  삭제한 사람 아이디
 */
public record SupplierDeletedEvent(Long supplierId, String deletedBy) {
}
