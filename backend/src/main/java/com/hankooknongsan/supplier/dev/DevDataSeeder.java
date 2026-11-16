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
 * 사용자와 업체가 모두 없을 때만 실행된다. 업체명·거래·인증·연락처는 모두 가상의 값이다.
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

    /** 사용자나 업체 자료가 있으면 보존하고, 빈 개발 DB에만 샘플을 넣는다. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0 || suppliers.count() > 0) {
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

    /** 업체 유형과 계약·인증을 함께 정의해 서로 무관한 예시가 연결되지 않도록 한다. */
    private record SampleSupplier(String name, String industry, String contractTitle,
                                  String certTypeCode, List<String> scopes) {
    }

    /** 실제 업체·거래·인증을 복사하지 않은 시연 전용 예시. 업종은 기존 비고에 표시한다. */
    private static final List<SampleSupplier> SUPPLIER_SAMPLES = List.of(
            new SampleSupplier("가상-봄딸기농원", "원료 농가 · 딸기", "딸기 원료 공급 계약", "GAP", List.of("딸기")),
            new SampleSupplier("가상-여름복숭아농원", "원료 농가 · 복숭아", "복숭아 원료 공급 계약", "GAP", List.of("복숭아")),
            new SampleSupplier("가상-햇살사과농원", "원료 농가 · 사과", "사과 원료 공급 계약", "GAP", List.of("사과")),
            new SampleSupplier("가상-파란블루베리농원", "원료 농가 · 블루베리", "블루베리 원료 공급 계약", "GAP", List.of("블루베리")),
            new SampleSupplier("가상-남쪽감귤농원", "원료 농가 · 감귤", "감귤 원료 공급 계약", "GAP", List.of("감귤")),
            new SampleSupplier("가상-산들매실농원", "원료 농가 · 매실", "매실 원료 공급 계약", "GAP", List.of("매실")),
            new SampleSupplier("가상-동산배농원", "원료 농가 · 배", "배 원료 공급 계약", "GAP", List.of("배")),
            new SampleSupplier("가상-들녘포도농원", "원료 농가 · 포도", "포도 원료 공급 계약", "GAP", List.of("포도")),
            new SampleSupplier("가상-온기과일가공", "식품 가공 · 냉동 과일", "냉동 과일 원료 공급 계약",
                    "HACCP", List.of("과채가공품 · 제1가공라인", "과채가공품 · 제2가공라인")),
            new SampleSupplier("가상-담은퓨레", "식품 가공 · 과일 퓨레", "과일 퓨레 원료 공급 계약",
                    "HACCP", List.of("딸기 퓨레", "복숭아 퓨레")),
            new SampleSupplier("가상-달콤잼원료", "식품 가공 · 잼 원료", "잼 원료 공급 계약",
                    "HACCP", List.of("잼류", "과채가공품")),
            new SampleSupplier("가상-고운디저트원료", "식품 가공 · 디저트 원료", "디저트 원료 공급 계약",
                    "ISO22000", List.of("디저트 원료 제조시설", "냉동 원료 가공시설")),
            new SampleSupplier("가상-맑은과즙원료", "식품 가공 · 과즙 농축액", "과즙 농축액 공급 계약",
                    "FSSC22000", List.of("과즙 원료 제1공장", "과즙 원료 제2공장")),
            new SampleSupplier("가상-정다운과채가공", "식품 가공 · 혼합 과채 원료", "혼합 과채 원료 공급 계약",
                    "HACCP", List.of("과채주스 원료", "과채가공품")),
            new SampleSupplier("가상-든든유리용기", "포장재 · 유리병·뚜껑", "유리병 포장재 공급 계약", null, List.of()),
            new SampleSupplier("가상-깔끔포장필름", "포장재 · 파우치·필름", "필름 포장재 공급 계약", null, List.of()),
            new SampleSupplier("가상-반듯종이상자", "포장재 · 골판지 상자", "골판지 포장재 공급 계약", null, List.of()),
            new SampleSupplier("가상-신선냉장물류", "물류 · 냉장 배송", "냉장 배송 위탁 계약", null, List.of()),
            new SampleSupplier("가상-서늘냉동창고", "물류 · 냉동 보관", "냉동 보관 위탁 계약", null, List.of()),
            new SampleSupplier("가상-빠른원료운송", "물류 · 원료 운송", "원료 운송 위탁 계약", null, List.of()));

    /**
     * 가상 협력업체 20곳을 만든다.
     * 거래중 17곳, 거래중지 2곳, 거래종료 1곳이며 담당자는 김담당·이담당에게 번갈아 배정한다.
     */
    private List<Supplier> seedSuppliers(List<AppUser> staff) {
        List<Supplier> saved = new ArrayList<>();
        for (int i = 0; i < SUPPLIER_SAMPLES.size(); i++) {
            SampleSupplier sample = SUPPLIER_SAMPLES.get(i);
            SupplierStatus status = i == 4 || i == 15 ? SupplierStatus.SUSPENDED
                    : i == 19 ? SupplierStatus.TERMINATED : SupplierStatus.ACTIVE;
            Supplier s = new Supplier(sample.name(), status);
            s.update(sample.name(), null, status, "가상 담당" + (i + 1), "010-0000-%04d".formatted(i + 1),
                    "contact%02d@example.com".formatted(i + 1), staff.get(1 + i % 2),
                    "[가상 시연 데이터] 업종: " + sample.industry() + ". 실제 업체·거래 정보가 아닙니다.");
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
        LocalDate today = clock.today();
        for (int i = 0; i < CONTRACT_OFFSETS.length; i++) {
            Supplier s = list.get(i % list.size());
            SampleSupplier sample = SUPPLIER_SAMPLES.get(i % list.size());
            Integer offset = CONTRACT_OFFSETS[i];
            LocalDate end = offset == null ? null : today.plusDays(offset);
            LocalDate start = end == null ? today.minusDays(400) : end.minusYears(1).plusDays(1);
            Contract c = new Contract(s);
            c.update(s, i % 3 == 0 ? "" : "DEMO-CT-%03d".formatted(i + 1), sample.contractTitle(), start, end,
                    i == 12, s.getOwner(), i % 3 == 0 ? "https://docs.example.com/contracts/DEMO-%03d".formatted(i + 1) : null,
                    "[가상 시연 데이터] " + sample.industry() + " 거래 예시. 계약·문서 주소는 실제 자료가 아닙니다.");
            if (i == 3 || i == 5) {
                c.changeRenewalStatus(RenewalStatus.IN_PROGRESS);
            }
            contracts.save(c);
        }
    }

    /** 농가·식품 가공 업체에 맞는 인증 30건을 만든다. 같은 업체의 적용 대상 차이와 미입력도 포함한다. */
    private void seedCertifications(List<Supplier> list) {
        if (!certTypes.existsById("FSSC22000")) {
            certTypes.save(new CertType("FSSC22000", "FSSC 22000", "fssc22000", certTypes.nextSortOrder()));
        }
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < SUPPLIER_SAMPLES.size(); i++) {
            if (SUPPLIER_SAMPLES.get(i).certTypeCode() != null) eligible.add(i);
        }
        LocalDate today = clock.today();
        for (int i = 0; i < CERT_OFFSETS.length; i++) {
            int supplierIndex = eligible.get(i % eligible.size());
            Supplier s = list.get(supplierIndex);
            SampleSupplier sample = SUPPLIER_SAMPLES.get(supplierIndex);
            CertType type = certTypes.findById(sample.certTypeCode()).orElseThrow();
            String scope = i == CERT_OFFSETS.length - 1 ? null
                    : sample.scopes().get((i / eligible.size()) % sample.scopes().size());
            Integer offset = CERT_OFFSETS[i];
            LocalDate expiry = offset == null ? null : today.plusDays(offset);
            LocalDate issued = expiry == null ? today.minusDays(300) : expiry.minusYears(3).plusDays(1);
            Certification x = new Certification(s);
            x.update(s, type, "DEMO-%s-%03d".formatted(type.getCode(), i + 1), scope, issued, expiry,
                    s.getOwner(), null, "[가상 시연 데이터] 실제 인증번호·인증 보유 사실이 아닌 기능 확인용 예시입니다.");
            if (i == 4) {
                x.changeRenewalStatus(RenewalStatus.IN_PROGRESS);
            }
            certifications.save(x);
        }
    }
}
