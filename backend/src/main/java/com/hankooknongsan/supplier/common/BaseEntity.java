package com.hankooknongsan.supplier.common;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 업무 엔티티의 공통 부모.
 * 등록자·등록일시·수정자·수정일시를 JPA Auditing으로 자동 기록한다 (비기능 요구사항: 감사).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /** 등록일시 */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 등록자 아이디 */
    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    /** 최종 수정일시 */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 최종 수정자 아이디 */
    @LastModifiedBy
    private String updatedBy;
}
