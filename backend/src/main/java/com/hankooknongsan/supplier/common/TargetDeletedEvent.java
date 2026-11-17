package com.hankooknongsan.supplier.common;

/**
 * 계약·인증이 삭제되었음을 알리는 이벤트 (#44).
 * 알림 기능은 그 대상의 알림을 없애고, 파일 기능은 첨부를 함께 삭제한다. (계약·인증 → 알림·파일 방향의 직접 의존을 피하기 위함)
 *
 * @param targetType 대상 종류
 * @param targetId   계약 또는 인증 ID
 * @param deletedBy  삭제한 사람 아이디
 */
public record TargetDeletedEvent(TargetType targetType, Long targetId, String deletedBy) {
}
