package com.hankooknongsan.supplier.common;

import java.time.LocalDateTime;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 지운 표시만 남기는(논리 삭제) 업무 엔티티의 공통 부모 (#44).
 * 삭제한 사람과 시각을 남기고, 각 엔티티의 {@code @SQLRestriction("deleted_at is null")}로 모든 조회에서 빠진다.
 */
@Getter
@MappedSuperclass
public abstract class DeletableEntity extends BaseEntity {

    /** 삭제 일시. null이면 삭제되지 않은 건 */
    private LocalDateTime deletedAt;

    /** 삭제한 사람 아이디 */
    private String deletedBy;

    /** 지운 표시를 남긴다. 행은 DB에 그대로 있어 필요하면 되살릴 수 있다. */
    public void delete(String by, LocalDateTime at) {
        this.deletedBy = by;
        this.deletedAt = at;
    }
}
