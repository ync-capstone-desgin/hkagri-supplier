package com.hankooknongsan.supplier.notification.job;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.certification.CertificationRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.notification.Channel;
import com.hankooknongsan.supplier.notification.Notification;
import com.hankooknongsan.supplier.notification.NotificationRepository;
import com.hankooknongsan.supplier.notification.rule.NotificationRule;
import com.hankooknongsan.supplier.notification.rule.NotificationRuleRepository;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료 점검 → 알림 대상 생성 서비스 (FR-010).
 * <ol>
 *   <li>사용 중인 알림 기준마다, 오늘부터 가장 먼 기준일(예: 90일) 안에 만료되는 유효 계약·인증을 찾는다.</li>
 *   <li>거래중이 아닌 업체, 이미 갱신완료·갱신안함 처리된 건은 뺀다.</li>
 *   <li>남은 일수에 해당하는 <b>가장 작은 기준일 하나</b>를 고른다 (D-5면 7일 기준, D-20이면 30일 기준).
 *       서버가 며칠 멈췄다 돌아와도 90·30·7일 알림이 한꺼번에 쏟아지지 않게 하기 위함이다.</li>
 *   <li>담당자(없으면 관리자 전원)에게 알림을 만들고, 채널별 발송 대기 건을 같은 트랜잭션으로 저장한다.</li>
 *   <li>같은 대상·기준·만료일·수신자의 알림이 이미 있으면 건너뛴다 (VT-006).</li>
 * </ol>
 * "만료일 = 오늘 + N"이 아니라 "만료일 ≤ 오늘 + N"으로 찾으므로 점검이 하루 빠져도 다음 실행에서 잡힌다.
 */
@Service
@RequiredArgsConstructor
public class NotificationScanService {

    private final NotificationRuleRepository rules;
    private final ContractRepository contracts;
    private final CertificationRepository certifications;
    private final NotificationRepository notifications;
    private final AppUserRepository users;
    private final AppClock clock;

    /**
     * 점검 결과
     *
     * @param today     점검 기준일
     * @param targets   기간 안에 들어온 대상 수 (제외 전)
     * @param created   새로 만든 알림 수
     * @param skipped   이미 보낸 적이 있어 건너뛴 수
     * @param excluded  거래중지 업체·갱신완료 등으로 뺀 대상 수
     */
    public record ScanResult(LocalDate today, int targets, int created, int skipped, int excluded) {
    }

    /** 대상 하나를 알림 생성에 필요한 값만으로 표현한 것 */
    private record Target(TargetType type, Long id, Supplier supplier, String name, String number,
                          LocalDate expiryDate, RenewalStatus renewalStatus, AppUser owner) {
    }

    /** 오늘 날짜로 점검한다. */
    public ScanResult scanToday() {
        return scan(clock.today());
    }

    /** 지정한 날짜를 오늘로 보고 점검한다. */
    @Transactional
    public ScanResult scan(LocalDate today) {
        int targets = 0, created = 0, skipped = 0, excluded = 0;
        List<AppUser> admins = users.findByRole(Role.ADMIN);

        for (NotificationRule rule : rules.findByEnabledTrue()) {
            List<Integer> days = rule.days();
            if (days.isEmpty()) {
                continue;
            }
            LocalDate until = today.plusDays(days.getLast());
            for (Target t : findTargets(rule.getTargetType(), today, until)) {
                targets++;
                if (t.supplier().getStatus() != SupplierStatus.ACTIVE || t.renewalStatus().closed()) {
                    excluded++;
                    continue;
                }
                int daysLeft = (int) ChronoUnit.DAYS.between(today, t.expiryDate());
                int ruleDays = days.stream().filter(d -> d >= daysLeft).findFirst().orElseThrow();
                List<AppUser> recipients = t.owner() != null ? List.of(t.owner()) : admins;
                for (AppUser recipient : recipients) {
                    if (notifications.existsByTargetTypeAndTargetIdAndRuleDaysAndExpiryDateAndRecipientId(
                            t.type(), t.id(), ruleDays, t.expiryDate(), recipient.getId())) {
                        skipped++;
                        continue;
                    }
                    notifications.save(newNotification(t, ruleDays, daysLeft, recipient, rule.channelSet()));
                    created++;
                }
            }
        }
        return new ScanResult(today, targets, created, skipped, excluded);
    }

    /** 기간(today ~ until) 안에 만료되는 유효 계약 또는 인증을 찾는다. */
    private List<Target> findTargets(TargetType type, LocalDate today, LocalDate until) {
        if (type == TargetType.CONTRACT) {
            return contracts.findEnding(today, until, null, null).stream()
                    .map(c -> new Target(type, c.getId(), c.getSupplier(), c.getTitle(), c.getContractNumber(),
                            c.getEndDate(), c.getRenewalStatus(), c.getOwner()))
                    .toList();
        }
        return certifications.findExpiring(today, until, null, null).stream()
                .map(x -> new Target(type, x.getId(), x.getSupplier(), x.getCertType().getName(),
                        x.getCertificateNumber(), x.getExpiryDate(), x.getRenewalStatus(), x.getOwner()))
                .toList();
    }

    /**
     * 알림과 채널별 발송 대기 건을 만든다.
     * 이메일은 수신자 이메일이 등록돼 있을 때만 대기열에 넣는다.
     */
    private Notification newNotification(Target t, int ruleDays, int daysLeft, AppUser recipient, Set<Channel> channels) {
        String dday = daysLeft == 0 ? "D-DAY" : "D-" + daysLeft;
        String title = "[%s] %s · %s 만료 예정".formatted(dday, t.supplier().getName(), t.name());
        String message = "%s %s(%s)이(가) %s에 만료됩니다. (만료 %d일 전 알림) 갱신 진행 상황을 확인해 주세요."
                .formatted(t.supplier().getName(), t.name(), t.number(), t.expiryDate(), ruleDays);
        Notification n = new Notification(t.type(), t.id(), ruleDays, t.expiryDate(), recipient, t.supplier(),
                title, message, clock.now());
        for (Channel channel : channels) {
            // 알림메일을 꺼둔 사용자(FR-028)이거나 이메일 주소가 없으면 이메일 발송건 자체를 만들지 않는다.
            // 화면(IN_APP) 발송건은 이 조건에 걸리지 않으므로 그대로 만들어진다.
            if (channel == Channel.EMAIL
                    && (!recipient.isNotifyEmail() || recipient.getEmail() == null || recipient.getEmail().isBlank())) {
                continue;
            }
            n.addDelivery(channel, clock.now());
        }
        return n;
    }
}
