package com.hankooknongsan.supplier.common;

/**
 * 삭제했던 협력업체를 되살린다는 것을 알리는 이벤트 (#72).
 * 파일 기능이 받아 삭제할 때 함께 지워진 일반 자료를 되살린다. 업체의 지운 표시를 지우기 전에 발행한다.
 *
 * @param supplierId 되살리는 협력업체 ID
 */
public record SupplierRestoredEvent(Long supplierId) {
}
