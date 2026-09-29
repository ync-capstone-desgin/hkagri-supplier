package com.hankooknongsan.supplier.common;

/**
 * 계약·인증이 더 이상 알림이 필요 없게 되었음을 알리는 이벤트.
 * 갱신 등록(이전 건 갱신완료) 또는 처리상태를 "갱신안함"으로 바꿨을 때 발생한다.
 * 알림 기능이 이 이벤트를 받아 관련 알림을 완료 처리한다. (계약·인증 → 알림 방향의 직접 의존을 피하기 위함)
 *
 * @param targetType 대상 종류
 * @param targetId   계약 또는 인증 ID
 */
public record RenewalClosedEvent(TargetType targetType, Long targetId) {
}
