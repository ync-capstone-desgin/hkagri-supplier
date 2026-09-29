package com.hankooknongsan.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 협력업체 통합관리 PoC 백엔드 애플리케이션 시작점.
 * 만료 점검·알림 발송 같은 정기 작업을 위해 스케줄링을 활성화한다.
 */
@EnableScheduling
@SpringBootApplication
public class SupplierPocApplication {

    /** 스프링 부트 애플리케이션을 실행한다. */
    public static void main(String[] args) {
        SpringApplication.run(SupplierPocApplication.class, args);
    }
}
