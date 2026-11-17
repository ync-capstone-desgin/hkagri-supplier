package com.hankooknongsan.supplier.history;

import java.time.LocalDateTime;
import java.util.List;

/** 변경 이력 API의 응답 형식 모음 */
public final class ChangeLogDtos {

    private ChangeLogDtos() {
    }

    /**
     * 바뀐 항목 하나.
     *
     * @param label  항목 이름 (예: 종료일)
     * @param before 이전 값. 없었으면 null
     * @param after  바뀐 값. 지워졌으면 null
     */
    public record FieldChange(String label, String before, String after) {
    }

    /** 변경 이력 한 줄. 종류·작업은 화면에 바로 쓰도록 한글 이름도 함께 준다. */
    public record ChangeItem(Long id, ChangeTarget targetType, String targetLabel, Long targetId, Long supplierId,
                             String supplierName, String targetName, ChangeAction action, String actionLabel,
                             List<FieldChange> changes, String actor, String actorName, LocalDateTime createdAt) {
    }
}
