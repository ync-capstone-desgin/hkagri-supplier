package com.hankooknongsan.supplier.dev;

import java.util.List;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.auth.Role;
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
 * 기능이 추가될 때마다 계약·인증 등 샘플을 이 클래스에 늘려 간다.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final SupplierRepository suppliers;
    private final PasswordEncoder encoder;

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
        seedSuppliers(staff);
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
    private void seedSuppliers(List<AppUser> staff) {
        String[] names = {
                "가람농산", "나래유통", "다온영농조합", "라온푸드", "마루청과",
                "바른들녘", "사계절농원", "아름채소", "자연담은", "초록마을영농",
                "푸른들식품", "하늘목장", "한빛곡물", "해오름농장", "새벽청과",
                "들꽃유기농", "산들과수원", "강변채소", "누리농산물", "온새미로"};
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
        }
    }
}
