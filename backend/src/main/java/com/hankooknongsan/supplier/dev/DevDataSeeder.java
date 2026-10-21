package com.hankooknongsan.supplier.dev;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.certification.CertType;
import com.hankooknongsan.supplier.certification.CertTypeRepository;
import com.hankooknongsan.supplier.certification.Certification;
import com.hankooknongsan.supplier.certification.CertificationRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.contract.Contract;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 비식별 샘플 데이터 생성기 (dev 프로파일 전용).
 * DB가 비어 있을 때만 실행된다. 업체명·연락처는 모두 가상의 값이다.
 * 기능이 추가될 때마다 샘플을 이 클래스에 늘려 간다. (현재: 사용자, 협력업체, 계약, 인증)
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final SupplierRepository suppliers;
    private final ContractRepository contracts;
    private final CertificationRepository certifications;
    private final CertTypeRepository certTypes;
    private final PasswordEncoder encoder;
    private final AppClock clock;

    /** 시연 계정 공통 비밀번호. 값은 README와 .env.example에 기록한다. */
    @Value("${app.seed-password}")
    private String seedPassword;

    /** 애플리케이션 시작 시 사용자가 한 명도 없으면 샘플 데이터를 넣는다. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        List<AppUser> staff = seedUsers();
        List<Supplier> list = seedSuppliers(staff);
        seedContracts(list);
        seedCertifications(list);
    }

    /** 역할별 시연 계정 4개(관리자 1, 담당자 2, 조회자 1)를 만든다. */
    private List<AppUser> seedUsers() {
        String pw = encoder.encode(seedPassword);
        return users.saveAll(List.of(
                new AppUser("admin", pw, "관리자", "admin@supplier-poc.local", Role.ADMIN),
                new AppUser("manager1", pw, "김담당", "manager1@supplier-poc.local", Role.MANAGER),
                new AppUser("manager2", pw, "이담당", "manager2@supplier-poc.local", Role.MANAGER),
                new AppUser("viewer", pw, "박조회", "viewer@supplier-poc.local", Role.VIEWER)));
    }

    /**
     * 가상 협력업체 20곳을 만든다.
     * 거래중 17곳, 거래중지 2곳, 거래종료 1곳이며 담당자는 김담당·이담당에게 번갈아 배정한다.
     */
    private List<Supplier> seedSuppliers(List<AppUser> staff) {
        String[] names = {
                "가람농산", "나래유통", "다온영농조합", "라온푸드", "마루청과",
                "바른들녘", "사계절농원", "아름채소", "자연담은", "초록마을영농",
                "푸른들식품", "하늘목장", "한빛곡물", "해오름농장", "새벽청과",
                "들꽃유기농", "산들과수원", "강변채소", "누리농산물", "온새미로"};
        List<Supplier> saved = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            SupplierStatus status = i == 17 || i == 18 ? SupplierStatus.SUSPENDED
                    : i == 19 ? SupplierStatus.TERMINATED : SupplierStatus.ACTIVE;
            Supplier s = new Supplier(names[i], status);
            s.update(names[i], "%03d-%02d-%05d".formatted(100 + i, 10 + i, 10000 + i * 37), status,
                    "업체담당" + (i + 1), "010-0000-%04d".formatted(i + 1),
                    "contact%02d@example.com".formatted(i + 1), staff.get(1 + i % 2), null);
            suppliers.save(s);
            suppliers.flush();
            s.assignCode("SUP-%04d".formatted(s.getId()));
            saved.add(s);
        }
        return saved;
    }

    /**
     * 만료일 경계 사례. 오늘 기준 며칠 뒤에 만료되는지를 뜻하며 null은 무기한(만료일 없음)이다.
     * D-0, 7·8, 30·31, 90·91일 경계와 이미 만료된 사례를 반드시 포함한다 (VT-004, VT-005).
     */
    private static final Integer[] CONTRACT_OFFSETS = {
            -30, -1, 0, 7, 8, 30, 31, 90, 91, null,
            180, 365, 400, 45, 14, 3, 60, 120, 200, 250,
            300, 500, 20, 5, 75, 150, 10, 25, 100, 330};

    /** 인증 만료일 경계 사례. 계약과 겹치지 않게 조금씩 다른 값을 섞는다. */
    private static final Integer[] CERT_OFFSETS = {
            -60, -2, 0, 6, 7, 29, 30, 89, 90, null,
            365, 540, 15, 1, 45, 120, 210, 400, 12, 60,
            95, 700, 28, 4, 150, 33, 18, 250, 80, 9};

    /**
     * 계약 30건을 만든다. 업체 20곳에 순서대로 배정하므로 앞쪽 10곳은 계약이 2건씩이다.
     * 한 건은 자동연장 계약, 두 건은 갱신진행 중 상태로 둔다.
     */
    private void seedContracts(List<Supplier> list) {
        String[] titles = {"농산물 납품 기본계약", "물류 위탁 계약", "포장재 공급 계약", "품질보증 협약", "저온창고 임차 계약"};
        LocalDate today = clock.today();
        for (int i = 0; i < CONTRACT_OFFSETS.length; i++) {
            Supplier s = list.get(i % list.size());
            Integer offset = CONTRACT_OFFSETS[i];
            LocalDate end = offset == null ? null : today.plusDays(offset);
            LocalDate start = end == null ? today.minusDays(400) : end.minusYears(1).plusDays(1);
            Contract c = new Contract(s);
            c.update(s, "CT-%d-%03d".formatted(start.getYear(), i + 1), titles[i % titles.length], start, end,
                    i == 12, s.getOwner(), i % 3 == 0 ? "https://docs.example.com/contracts/CT-%03d".formatted(i + 1) : null,
                    null);
            if (i == 3 || i == 5) {
                c.changeRenewalStatus(RenewalStatus.IN_PROGRESS);
            }
            contracts.save(c);
        }
    }

    /** 인증 30건을 만든다. 인증 종류는 기준정보 순서대로 돌아가며 배정한다. */
    private void seedCertifications(List<Supplier> list) {
        List<CertType> types = certTypes.findAllByOrderBySortOrder();
        LocalDate today = clock.today();
        for (int i = 0; i < CERT_OFFSETS.length; i++) {
            Supplier s = list.get((i + 5) % list.size());
            CertType type = types.get(i % types.size());
            Integer offset = CERT_OFFSETS[i];
            LocalDate expiry = offset == null ? null : today.plusDays(offset);
            LocalDate issued = expiry == null ? today.minusDays(300) : expiry.minusYears(3).plusDays(1);
            Certification x = new Certification(s);
            x.update(s, type, "%s-%06d".formatted(type.getCode(), 100000 + i * 7919 % 900000), issued, expiry,
                    s.getOwner(), null, null);
            if (i == 4) {
                x.changeRenewalStatus(RenewalStatus.IN_PROGRESS);
            }
            certifications.save(x);
        }
    }
}
