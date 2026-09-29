package com.hankooknongsan.supplier.notification;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/** 발송 기록(아웃박스) 저장소 */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    /**
     * 지금 보낼 차례인 대기 건을 오래된 순으로 꺼낸다.
     * 행 잠금 + SKIP LOCKED로 가져오므로, 서버를 여러 대 띄워도 같은 건을 두 번 보내지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select d from NotificationDelivery d
            where d.status = com.hankooknongsan.supplier.notification.DeliveryStatus.PENDING
              and d.nextAttemptAt <= :now
            order by d.nextAttemptAt, d.id
            """)
    List<NotificationDelivery> findDue(@Param("now") LocalDateTime now, Pageable pageable);

    /** 최근 발송 기록을 조회한다. (관리자 발송 이력 화면) */
    @EntityGraph(attributePaths = {"notification", "notification.recipient"})
    @Query("select d from NotificationDelivery d order by d.createdAt desc, d.id desc")
    List<NotificationDelivery> findRecent(Pageable pageable);
}
