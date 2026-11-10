package com.hankooknongsan.supplier.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hankooknongsan.supplier.TestcontainersConfig;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.certification.CertificationRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.RenewalStatus;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.supplier.Supplier;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 실제 dev 시작 시 생성되는 샘플의 업무 연결과 기존 자료 보호를 별도 PostgreSQL에서 검증한다. */
@SpringBootTest(properties = {"app.scheduling-enabled=false", "app.file.dir=build/test-files"})
@ActiveProfiles("dev")
@Import(TestcontainersConfig.class)
@Transactional
class DevDataSeederTest {

    @Autowired DevDataSeeder seeder;
    @Autowired AppUserRepository users;
    @Autowired SupplierRepository suppliers;
    @Autowired ContractRepository contracts;
    @Autowired CertificationRepository certifications;
    @Autowired AppClock clock;
    @Autowired EntityManager em;

    /** 새 개발 DB에는 실제 자료와 혼동하지 않는 가상 업체와 기존 규모의 시연 항목을 만든다. */
    @Test
    void 새_DB에_가상_업종_샘플을_만든다() {
        assertThat(users.count()).isEqualTo(4);
        assertThat(suppliers.count()).isEqualTo(20);
        assertThat(contracts.count()).isEqualTo(30);
        assertThat(certifications.count()).isEqualTo(30);
        assertThat(suppliers.findAll()).allSatisfy(s -> {
            assertThat(s.getName()).startsWith("가상-");
            assertThat(s.getBusinessNumber()).isNull();
            assertThat(s.getContactEmail()).endsWith("@example.com");
            assertThat(s.getMemo()).contains("가상 시연 데이터");
        });
        var memos = suppliers.findAll().stream().map(Supplier::getMemo).toList();
        for (String business : new String[] {"원료 농가", "식품 가공", "포장재", "물류"}) {
            assertThat(memos).anySatisfy(memo -> assertThat(memo).contains(business));
        }
        assertThat(certifications.findAll()).allSatisfy(c -> {
            assertThat(c.getCertificateNumber()).startsWith("DEMO-");
            assertThat(c.getMemo()).contains("가상 시연 데이터");
        });
    }

    /** 업체 유형에 맞는 계약을 연결하고 포장재·물류에는 농가·식품 가공 인증을 무작위로 배정하지 않는다. */
    @Test
    void 업종에_맞는_계약과_인증을_연결한다() {
        assertThat(contracts.findAll()).allSatisfy(c -> {
            String business = Objects.toString(c.getSupplier().getMemo(), "");
            if (business.contains("원료 농가")) assertThat(c.getTitle()).contains("원료 공급");
            if (business.contains("포장재")) assertThat(c.getTitle()).contains("포장재 공급");
            if (business.contains("물류")) assertThat(c.getTitle()).containsAnyOf("배송", "운송", "보관");
        });
        assertThat(certifications.findAll()).allSatisfy(c -> {
            String business = c.getSupplier().getMemo();
            assertThat(business).isNotNull().doesNotContain("포장재", "물류");
            if (business.contains("원료 농가")) assertThat(c.getCertType().getCode()).isEqualTo("GAP");
            if (business.contains("식품 가공")) assertThat(c.getCertType().getCode()).isIn("HACCP", "ISO22000", "FSSC22000");
        });
        assertThat(certifications.findAll()).extracting(c -> c.getCertType().getCode())
                .contains("HACCP", "ISO22000", "FSSC22000", "GAP");
        var scopesBySupplier = certifications.findAll().stream().filter(c -> c.getCertType().getCode().equals("HACCP"))
                .collect(Collectors.groupingBy(c -> c.getSupplier().getId(),
                        Collectors.mapping(c -> c.getScope(), Collectors.toSet())));
        assertThat(scopesBySupplier.values()).anySatisfy(scopes -> assertThat(scopes).hasSizeGreaterThanOrEqualTo(2).doesNotContainNull());
    }

    /** 만료 경계·빈 날짜·진행 상태·선택 입력 사례가 새 샘플에서도 시연 가능하다. */
    @Test
    void 만료_경계와_선택_입력_사례를_유지한다() {
        var allContracts = contracts.findAll();
        assertThat(allContracts.stream().map(c -> c.getEndDate() == null ? null : ChronoUnit.DAYS.between(clock.today(), c.getEndDate())).toList())
                .contains(-1L, 0L, 7L, 8L, 30L, 31L, 90L, 91L, null);
        assertThat(allContracts).anySatisfy(c -> assertThat(c.getContractNumber()).isEmpty());
        assertThat(allContracts).anySatisfy(c -> assertThat(c.getContractNumber()).startsWith("DEMO-"));
        assertThat(allContracts.stream().filter(c -> c.isAutoRenew()).count()).isEqualTo(1);
        assertThat(allContracts.stream().filter(c -> c.getRenewalStatus() == RenewalStatus.IN_PROGRESS).count()).isEqualTo(2);
        var allCerts = certifications.findAll();
        assertThat(allCerts.stream().map(c -> c.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(clock.today(), c.getExpiryDate())).toList())
                .contains(-2L, 0L, 6L, 7L, 29L, 30L, 89L, 90L, null);
        assertThat(allCerts).anySatisfy(c -> assertThat(c.getScope()).isNull());
        assertThat(allCerts).anySatisfy(c -> assertThat(c.getScope()).isNotBlank());
    }

    /** 재시작 시 사용자가 수정한 업체와 항목 수를 보존한다. */
    @Test
    void 재실행해도_기존_자료를_덮어쓰지_않는다() {
        Supplier s = suppliers.findAll().getFirst();
        Long id = s.getId();
        s.update("직접 수정한 가상 업체", null, s.getStatus(), null, null, null, s.getOwner(), "직접 입력한 메모");
        suppliers.saveAndFlush(s);
        seeder.run(null);
        em.clear();
        assertThat(suppliers.findById(id).orElseThrow().getName()).isEqualTo("직접 수정한 가상 업체");
        assertThat(users.count()).isEqualTo(4);
        assertThat(suppliers.count()).isEqualTo(20);
        assertThat(contracts.count()).isEqualTo(30);
        assertThat(certifications.count()).isEqualTo(30);
    }

    /** 사용자 테이블만 비어 있어도 기존 업무 자료가 있으면 가상 데이터를 추가하지 않는다. */
    @Test
    void 사용자_없이_기존_업체가_있어도_샘플을_추가하지_않는다() {
        certifications.deleteAllInBatch();
        contracts.deleteAllInBatch();
        suppliers.deleteAllInBatch();
        users.deleteAllInBatch();
        em.clear();
        Supplier existing = suppliers.saveAndFlush(new Supplier("기존 자료 보호 검증", SupplierStatus.ACTIVE));
        seeder.run(null);
        assertThat(users.count()).isZero();
        assertThat(suppliers.count()).isEqualTo(1);
        assertThat(contracts.count()).isZero();
        assertThat(certifications.count()).isZero();
        assertThat(suppliers.findById(existing.getId()).orElseThrow().getName()).isEqualTo("기존 자료 보호 검증");
    }
}
