package com.hankooknongsan.supplier.notification;

import java.time.LocalDate;
import java.util.List;

import com.hankooknongsan.supplier.common.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 알림 저장소 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 같은 대상·기준·만료일·수신자의 알림이 이미 있는지 확인한다. (중복 생성 방지) */
    boolean existsByTargetTypeAndTargetIdAndRuleDaysAndExpiryDateAndRecipientId(
            TargetType targetType, Long targetId, int ruleDays, LocalDate expiryDate, Long recipientId);

    /** 수신자의 알림함을 만료일이 가까운 순(같으면 최신순)으로 조회한다. actionStatus가 null이면 전체. */
    @EntityGraph(attributePaths = "supplier")
    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId and (:actionStatus is null or n.actionStatus = :actionStatus)
            order by n.expiryDate, n.createdAt desc, n.id desc
            """)
    Page<Notification> findInbox(@Param("recipientId") Long recipientId,
                                 @Param("actionStatus") ActionStatus actionStatus, Pageable pageable);

    /** 수신자의 처리상태별 알림 수를 센다. (🔔 배지의 미확인 수 등) */
    long countByRecipientIdAndActionStatus(Long recipientId, ActionStatus actionStatus);

    /** 한 대상에 대해 아직 완료되지 않은 알림을 찾는다. (갱신 시 자동 완료 처리용) */
    List<Notification> findByTargetTypeAndTargetIdAndActionStatusNot(TargetType targetType, Long targetId,
                                                                       ActionStatus actionStatus);

    /** 한 대상의 알림을 처리상태와 관계없이 모두 찾는다. (대상 삭제 시 알림 제거용) */
    List<Notification> findByTargetTypeAndTargetId(TargetType targetType, Long targetId);

    /** 수신자의 미확인 알림을 모두 찾는다. ([모두 확인] 처리용) */
    List<Notification> findByRecipientIdAndActionStatus(Long recipientId, ActionStatus actionStatus);

    /** 협력업체의 알림에 딸린 발송 기록을 완전히 지운다. (#77 업체 영구삭제) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from notification_delivery where notification_id in (select id from notification where supplier_id = :supplierId)", nativeQuery = true)
    int purgeDeliveriesOfSupplier(@Param("supplierId") Long supplierId);

    /** 협력업체의 알림을 완전히 지운다. (#77 업체 영구삭제) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from notification where supplier_id = :supplierId", nativeQuery = true)
    int purgeOfSupplier(@Param("supplierId") Long supplierId);
}
