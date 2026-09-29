package com.hankooknongsan.supplier.notification.rule;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hankooknongsan.supplier.common.BaseEntity;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.notification.Channel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 기준 설정 (대상 종류별 1건).
 * 기준일("90,30,7")과 채널("IN_APP,EMAIL")은 쉼표로 구분한 문자열로 저장해 DB 종류에 상관없이 쓰도록 한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 적용 대상 종류 (계약/인증) */
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    /** 만료 며칠 전에 알릴지. 예: "90,30,7" */
    private String daysBefore;

    /** 발송 채널. 예: "IN_APP,EMAIL" */
    private String channels;

    /** 사용 여부 */
    private boolean enabled;

    /** 기준일 목록을 작은 순으로 반환한다. 예: [7, 30, 90] */
    public List<Integer> days() {
        return Arrays.stream(daysBefore.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Integer::valueOf).sorted().toList();
    }

    /** 발송 채널 집합을 반환한다. */
    public Set<Channel> channelSet() {
        return Arrays.stream(channels.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Channel::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(Channel.class)));
    }

    /** 기준일·채널·사용 여부를 바꾼다. 기준일은 중복을 없애고 큰 순으로 저장한다. */
    public void update(List<Integer> days, Set<Channel> channels, boolean enabled) {
        this.daysBefore = days.stream().distinct().sorted((a, b) -> b - a).map(String::valueOf)
                .collect(Collectors.joining(","));
        this.channels = channels.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        this.enabled = enabled;
    }
}
