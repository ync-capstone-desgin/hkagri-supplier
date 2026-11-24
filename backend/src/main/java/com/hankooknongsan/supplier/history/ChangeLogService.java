package com.hankooknongsan.supplier.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.history.ChangeLogDtos.ChangeItem;
import com.hankooknongsan.supplier.history.ChangeLogDtos.FieldChange;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 변경 이력 기록·조회 서비스 (#45).
 * 협력업체·계약·인증 서비스가 등록·수정·갱신·삭제할 때 {@link #record}를 불러 같은 트랜잭션 안에서 이력을 남긴다.
 * 작업이 실패해 되돌려지면 이력도 함께 되돌려진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChangeLogService {

    private final ChangeLogRepository logs;
    private final AppUserRepository users;
    private final AuditorAware<String> auditor;
    private final AppClock clock;
    private final ObjectMapper mapper;

    /**
     * 변경 이력을 남긴다. 이전 값과 바뀐 값을 항목별로 비교해 달라진 항목만 저장한다.
     * 수정인데 달라진 항목이 없으면 남기지 않는다. 등록은 이전 값을 빈 맵으로, 삭제는 둘 다 빈 맵으로 준다.
     *
     * @param before 항목 이름 → 이전 값
     * @param after  항목 이름 → 바뀐 값 (항목 순서는 이 맵의 순서를 따른다)
     */
    @Transactional
    public void record(ChangeTarget targetType, Long targetId, Long supplierId, String supplierName, String targetName,
                       ChangeAction action, Map<String, String> before, Map<String, String> after) {
        List<FieldChange> changes = new ArrayList<>();
        after.forEach((label, value) -> {
            if (!Objects.equals(before.get(label), value)) {
                changes.add(new FieldChange(label, before.get(label), value));
            }
        });
        if (action == ChangeAction.UPDATE && changes.isEmpty()) {
            return;
        }
        String actor = auditor.getCurrentAuditor().orElse("system");
        String actorName = users.findByUsername(actor).map(AppUser::getName).orElse(actor);
        logs.save(new ChangeLog(targetType, targetId, supplierId, supplierName, targetName, action,
                mapper.writeValueAsString(changes), actor, actorName, clock.now()));
    }

    /**
     * 영구삭제했다는 사실을 남긴다 (#77). 대상 행이 이미 없으므로 업체명·대상 이름은 그 대상의 마지막 기록에서 옮겨 적는다.
     * 이전 기록이 없으면(이력 기능 도입 전에 지운 건) 이름 없이 남기고, 업체 ID는 알 수 없어 0으로 둔다.
     */
    @Transactional
    public void recordPurge(ChangeTarget targetType, Long targetId) {
        ChangeLog last = logs.findFirstByTargetTypeAndTargetIdOrderByIdDesc(targetType, targetId).orElse(null);
        String actor = auditor.getCurrentAuditor().orElse("system");
        String actorName = users.findByUsername(actor).map(AppUser::getName).orElse(actor);
        logs.save(new ChangeLog(targetType, targetId, last != null ? last.getSupplierId() : targetType == ChangeTarget.SUPPLIER ? targetId : 0L,
                last == null ? "" : last.getSupplierName(), last == null ? "" : last.getTargetName(),
                ChangeAction.PURGE, "[]", actor, actorName, clock.now()));
    }

    /** 한 업체와 그 업체의 계약·인증 이력을 최신순으로 조회한다. */
    public PageResponse<ChangeItem> forSupplier(Long supplierId, Pageable pageable) {
        return PageResponse.of(logs.findBySupplierIdOrderByCreatedAtDescIdDesc(supplierId, pageable), this::toItem);
    }

    /** 전체 이력을 검색한다. (관리자용) */
    public PageResponse<ChangeItem> search(ChangeTarget targetType, String keyword, Pageable pageable) {
        return PageResponse.of(logs.search(targetType, keyword == null ? "" : keyword.trim(), pageable), this::toItem);
    }

    /** 이력 엔티티를 응답 형식으로 바꾼다. 저장해 둔 JSON을 항목 목록으로 되돌린다. */
    private ChangeItem toItem(ChangeLog h) {
        List<FieldChange> changes = mapper.readValue(h.getChanges(), new TypeReference<>() {
        });
        return new ChangeItem(h.getId(), h.getTargetType(), h.getTargetType().label(), h.getTargetId(),
                h.getSupplierId(), h.getSupplierName(), h.getTargetName(), h.getAction(), h.getAction().label(),
                changes, h.getActor(), h.getActorName(), h.getCreatedAt(),
                h.getAction() == ChangeAction.DELETE
                        && !logs.existsByTargetTypeAndTargetIdAndIdGreaterThan(h.getTargetType(), h.getTargetId(), h.getId()));
    }
}
