package com.hankooknongsan.supplier.common;

/**
 * 삭제된 계약·인증을 영구삭제한다는 것을 알리는 이벤트 (#77).
 * 파일 기능이 받아 그 건의 첨부를 DB와 저장소에서 모두 지운다. 첨부가 대상을 가리키므로 대상 행을 지우기 전에 발행한다.
 *
 * @param targetType 대상 종류
 * @param targetId   계약 또는 인증 ID
 */
public record TargetPurgedEvent(TargetType targetType, Long targetId) {
}
