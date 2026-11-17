package com.hankooknongsan.supplier.common;

/**
 * 삭제했던 계약·인증을 되살린다는 것을 알리는 이벤트 (#72).
 * 파일 기능이 받아 삭제할 때 함께 지워진 첨부를 되살린다. 지운 시각을 비교해야 하므로 대상의 지운 표시를 지우기 전에 발행한다.
 *
 * @param targetType 대상 종류
 * @param targetId   계약 또는 인증 ID
 */
public record TargetRestoredEvent(TargetType targetType, Long targetId) {
}
