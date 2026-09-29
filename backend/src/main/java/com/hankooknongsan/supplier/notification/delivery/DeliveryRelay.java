package com.hankooknongsan.supplier.notification.delivery;

/**
 * 발송 대기열을 발송 처리로 넘기는 방식.
 * <p>
 * PoC는 {@link DbPollingDeliveryRelay}(DB를 주기적으로 확인)를 쓴다.
 * AWS로 배포하면 SQS로 넘기는 구현, 여러 시스템에 이벤트를 나눠야 하면 Kafka로 넘기는 구현으로
 * 이 인터페이스의 구현체만 바꾸면 된다. (설계서 2.3)
 */
public interface DeliveryRelay {

    /** 보낼 차례인 대기 건을 처리하고 처리한 건수를 반환한다. */
    int relay();
}
