package com.hankooknongsan.supplier;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트용 PostgreSQL 컨테이너 설정.
 * 테스트 실행 시 Docker로 실제 PostgreSQL을 띄우고 데이터소스를 자동 연결한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /** 테스트 전용 PostgreSQL 16 컨테이너를 만든다. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
