package com.hankooknongsan.supplier.notification.rule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.notification.Channel;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 기준 조회·변경 서비스 (FR-010).
 * 미팅에서 기준일·채널이 확정되면 코드 수정 없이 화면에서 바꿀 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationRuleService {

    /** 설정할 수 있는 가장 먼 기준일 */
    static final int MAX_DAYS = 365;

    private final NotificationRuleRepository rules;

    /** 알림 기준 변경 요청 */
    public record RuleRequest(@NotEmpty(message = "기준일을 하나 이상 입력해 주세요.") List<Integer> days,
                              @NotEmpty(message = "채널을 하나 이상 선택해 주세요.") Set<Channel> channels,
                              @NotNull Boolean enabled) {
    }

    /** 알림 기준 응답 */
    public record RuleItem(Long id, TargetType targetType, List<Integer> days, Set<Channel> channels, boolean enabled,
                           String updatedBy, LocalDateTime updatedAt) {

        /** 엔티티를 응답 형식으로 변환한다. 기준일은 큰 순(90, 30, 7)으로 보여준다. */
        static RuleItem of(NotificationRule r) {
            return new RuleItem(r.getId(), r.getTargetType(), r.days().reversed(), r.channelSet(), r.isEnabled(),
                    r.getUpdatedBy(), r.getUpdatedAt());
        }
    }

    /** 전체 알림 기준을 조회한다. */
    public List<RuleItem> list() {
        return rules.findAll(Sort.by("id")).stream().map(RuleItem::of).toList();
    }

    /** 알림 기준을 바꾼다. 기준일은 0~365일 사이여야 한다. */
    @Transactional
    public RuleItem update(Long id, RuleRequest req) {
        NotificationRule rule = rules.findById(id).orElseThrow(() -> BusinessException.notFound("알림 기준"));
        if (req.days().stream().anyMatch(d -> d == null || d < 0 || d > MAX_DAYS)) {
            throw BusinessException.invalid("days", "기준일은 0~" + MAX_DAYS + "일 사이로 입력해 주세요.");
        }
        rule.update(req.days(), req.channels(), req.enabled());
        rules.flush();
        return RuleItem.of(rule);
    }
}
