package com.hankooknongsan.supplier.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.common.TargetType;
import com.hankooknongsan.supplier.contract.Contract;
import com.hankooknongsan.supplier.contract.ContractDtos.ContractRequest;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.contract.ContractService;
import com.hankooknongsan.supplier.notification.delivery.DeliveryProcessor;
import com.hankooknongsan.supplier.notification.job.NotificationScanService;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 만료 점검·알림 생성·발송 통합 테스트 (FR-010, FR-011, VT-006).
 * 기준일을 테스트마다 다른 먼 미래 날짜로 옮겨 다른 테스트 데이터와 겹치지 않게 한다.
 * 메일 서버 포트를 일부러 잘못 지정해 이메일 발송 실패·재시도 기록도 확인한다.
 */
@SpringBootTest(properties = "spring.mail.port=1")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class NotificationScanTest {

    @Autowired AppClock clock;
    @Autowired NotificationScanService scanService;
    @Autowired DeliveryProcessor deliveryProcessor;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired AppUserRepository users;
    @Autowired SupplierRepository suppliers;
    @Autowired ContractRepository contracts;
    @Autowired ContractService contractService;
    @Autowired TransactionTemplate tx;

    static int seq = 0;
    LocalDate today;
    AppUser owner;
    Supplier active;

    /** 테스트마다 새 기준일(2040년 이후)과 담당자·거래중 업체를 만든다. */
    @BeforeEach
    void setUp() {
        today = LocalDate.of(2040, 1, 1).plusYears(seq++);
        clock.setToday(today);
        String username = "noti-" + System.nanoTime();
        owner = users.save(new AppUser(username, "{noop}x", "알림담당", username + "@test.local", Role.MANAGER));
        active = supplier("알림테스트업체", SupplierStatus.ACTIVE);
    }

    /** 기준일을 실제 날짜로 되돌린다. */
    @AfterEach
    void tearDown() {
        clock.setToday(null);
    }

    /** 담당자가 지정된 업체를 만든다. */
    Supplier supplier(String name, SupplierStatus status) {
        Supplier s = new Supplier(name, status);
        s.update(name, null, status, null, null, null, owner, null);
        return suppliers.save(s);
    }

    /** 오늘로부터 offset일 뒤 끝나는 계약을 만든다. */
    Contract contract(Supplier s, int offset, AppUser contractOwner) {
        Contract c = new Contract(s);
        c.update(s, "N-" + System.nanoTime(), "알림 테스트 계약", today.minusYears(1), today.plusDays(offset), false,
                contractOwner, null, null);
        return contracts.save(c);
    }

    /** 대상 계약에 생성된 알림의 적용 기준일 목록 */
    List<Integer> ruleDaysOf(Contract c) {
        return notifications.findAll().stream()
                .filter(n -> n.getTargetType() == TargetType.CONTRACT && n.getTargetId().equals(c.getId()))
                .map(Notification::getRuleDays).toList();
    }

    /** VT-006: 점검을 두 번 돌려도 같은 알림은 한 번만 생긴다. */
    @Test
    void 점검을_다시_실행해도_알림이_중복되지_않는다() {
        Contract c = contract(active, 5, owner);
        var first = scanService.scan(today);
        var second = scanService.scan(today);

        assertThat(ruleDaysOf(c)).containsExactly(7);
        assertThat(first.created()).isGreaterThanOrEqualTo(1);
        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isGreaterThanOrEqualTo(1);
    }

    /** 남은 일수에 해당하는 가장 작은 기준일 하나만 적용한다. (D-5 → 7일, D-20 → 30일, D-60 → 90일, D-91 → 없음) */
    @Test
    void 가장_작은_해당_기준일_하나만_적용한다() {
        Contract d5 = contract(active, 5, owner);
        Contract d20 = contract(active, 20, owner);
        Contract d60 = contract(active, 60, owner);
        Contract d91 = contract(active, 91, owner);
        scanService.scan(today);

        assertThat(ruleDaysOf(d5)).containsExactly(7);
        assertThat(ruleDaysOf(d20)).containsExactly(30);
        assertThat(ruleDaysOf(d60)).containsExactly(90);
        assertThat(ruleDaysOf(d91)).isEmpty();
    }

    /** 며칠 뒤 다시 점검해 더 가까운 기준일에 들어오면 그 기준으로 새 알림이 생긴다. */
    @Test
    void 기준일이_바뀌면_새_알림이_생긴다() {
        Contract c = contract(active, 10, owner);
        scanService.scan(today);                // D-10 → 30일 기준
        scanService.scan(today.plusDays(4));    // D-6  → 7일 기준
        assertThat(ruleDaysOf(c)).containsExactlyInAnyOrder(30, 7);
    }

    /** 거래중이 아닌 업체, 갱신완료·갱신안함 처리된 건은 알림을 만들지 않는다. */
    @Test
    void 거래중지_업체와_갱신_마감된_건은_제외한다() {
        Contract suspended = contract(supplier("중지업체", SupplierStatus.SUSPENDED), 5, owner);
        Contract notRenewing = contract(active, 5, owner);
        notRenewing.changeRenewalStatus(RenewalStatus.NOT_RENEWING);
        contracts.save(notRenewing);
        scanService.scan(today);

        assertThat(ruleDaysOf(suspended)).isEmpty();
        assertThat(ruleDaysOf(notRenewing)).isEmpty();
    }

    /** 담당자가 없으면 관리자 전원에게 알린다. */
    @Test
    void 담당자가_없으면_관리자에게_알린다() {
        AppUser admin = users.save(new AppUser("admin-" + System.nanoTime(), "{noop}x", "관리자", null, Role.ADMIN));
        Contract c = contract(active, 5, null);
        scanService.scan(today);

        List<Long> recipients = notifications.findAll().stream()
                .filter(n -> n.getTargetId().equals(c.getId()) && n.getTargetType() == TargetType.CONTRACT)
                .map(n -> n.getRecipient().getId()).toList();
        assertThat(recipients).contains(admin.getId()).doesNotContain(owner.getId());
    }

    /** 화면 알림은 발송완료, 이메일은 메일 서버 오류로 재시도 대기가 되고 사유가 기록된다. 업무 데이터(알림)는 남는다. */
    @Test
    void 이메일_발송에_실패하면_재시도_대기로_기록한다() {
        Contract c = contract(active, 3, owner);
        scanService.scan(today);
        deliveryProcessor.processDue();

        tx.executeWithoutResult(s -> {
            Notification n = notifications.findAll().stream()
                    .filter(x -> x.getTargetId().equals(c.getId()) && x.getTargetType() == TargetType.CONTRACT)
                    .findFirst().orElseThrow();
            var byChannel = n.getDeliveries();
            assertThat(byChannel).extracting(NotificationDelivery::getChannel).containsExactlyInAnyOrder(Channel.IN_APP, Channel.EMAIL);
            NotificationDelivery inApp = byChannel.stream().filter(d -> d.getChannel() == Channel.IN_APP).findFirst().orElseThrow();
            NotificationDelivery email = byChannel.stream().filter(d -> d.getChannel() == Channel.EMAIL).findFirst().orElseThrow();
            assertThat(inApp.getStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(email.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(email.getRetryCount()).isEqualTo(1);
            assertThat(email.getLastError()).isNotBlank();
            assertThat(email.getNextAttemptAt()).isAfter(clock.now());
        });
    }

    /** FR-028: 알림메일을 꺼두면 이메일 발송건은 안 만들어지고, 화면(IN_APP) 발송건은 그대로 만들어진다. */
    @Test
    void 알림메일을_꺼두면_이메일_발송건이_생기지_않는다() {
        AppUser noMail = users.save(new AppUser("noti-nomail-" + System.nanoTime(), "{noop}x", "메일끈담당",
                "nomail@test.local", Role.MANAGER));
        noMail.updateProfile(noMail.getName(), noMail.getEmail(), false);
        noMail = users.save(noMail);
        Contract c = contract(active, 5, noMail);
        scanService.scan(today);

        tx.executeWithoutResult(s -> {
            Notification n = notifications.findAll().stream()
                    .filter(x -> x.getTargetId().equals(c.getId()) && x.getTargetType() == TargetType.CONTRACT)
                    .findFirst().orElseThrow();
            assertThat(n.getDeliveries()).extracting(NotificationDelivery::getChannel).containsExactly(Channel.IN_APP);
        });
    }

    /** 계약을 갱신하면 기존 계약의 알림이 자동으로 완료 처리된다. */
    @Test
    void 갱신하면_기존_알림이_완료된다() {
        Contract c = contract(active, 5, owner);
        scanService.scan(today);
        contractService.renew(c.getId(), new ContractRequest(active.getId(), "RENEWED-" + System.nanoTime(), "갱신",
                today.plusDays(6), today.plusYears(1), false, owner.getId(), null, null));

        List<ActionStatus> statuses = notifications.findAll().stream()
                .filter(n -> n.getTargetId().equals(c.getId()) && n.getTargetType() == TargetType.CONTRACT)
                .map(Notification::getActionStatus).toList();
        assertThat(statuses).isNotEmpty().containsOnly(ActionStatus.DONE);
    }
}
