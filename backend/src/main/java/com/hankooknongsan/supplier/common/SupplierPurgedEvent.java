package com.hankooknongsan.supplier.common;

/**
 * 삭제된 협력업체를 영구삭제한다는 것을 알리는 이벤트 (#77).
 * 파일 기능은 업체 자료를, 알림 기능은 그 업체의 알림을 지운다. 둘 다 업체를 가리키므로 업체 행을 지우기 전에 발행한다.
 *
 * @param supplierId 영구삭제하는 협력업체 ID
 */
public record SupplierPurgedEvent(Long supplierId) {
}
