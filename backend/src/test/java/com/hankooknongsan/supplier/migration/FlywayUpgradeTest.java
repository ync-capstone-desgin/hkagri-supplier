package com.hankooknongsan.supplier.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** CI가 제공한 기준 SQL로 기존 DB를 만들고 업그레이드·이력 보호를 실제 PostgreSQL에서 검증한다. */
@Testcontainers
class FlywayUpgradeTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @TempDir
    Path migrations;

    /** 기준 브랜치의 DB에 현재 변경을 적용해도 대표 가상 업체 기록과 기존 적용 이력이 유지된다. */
    @Test
    void 기준_DB를_업그레이드하고_기존_업체를_보존한다() throws Exception {
        String baselineDir = System.getProperty("flyway.baseline-dir");
        assertThat(baselineDir).as("-PflywayBaselineDir로 기준 마이그레이션 폴더를 지정해야 합니다").isNotBlank();
        Path baseline = Path.of(baselineDir);
        assertThat(baseline).isDirectory();
        try (var files = Files.list(baseline)) {
            assertThat(files.anyMatch(path -> path.getFileName().toString().matches("V.+__.+\\.sql")))
                    .as("기준 폴더에는 버전 마이그레이션이 있어야 합니다").isTrue();
        }

        flyway("upgrade", "filesystem:" + baseline.toAbsolutePath()).migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into upgrade.supplier (code, name, status, memo, created_at, updated_at)
                    values ('CI-UPGRADE', 'CI 가상업체', 'ACTIVE', '업그레이드 보존 확인', current_timestamp, current_timestamp)
                    """);

            Flyway current = flyway("upgrade", "classpath:db/migration");
            current.migrate();
            current.validate();
            assertThat(current.info().pending()).isEmpty();
            try (var rows = statement.executeQuery("select name, status, memo from upgrade.supplier where code = 'CI-UPGRADE'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("name")).isEqualTo("CI 가상업체");
                assertThat(rows.getString("status")).isEqualTo("ACTIVE");
                assertThat(rows.getString("memo")).isEqualTo("업그레이드 보존 확인");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    /** 높은 버전을 이미 적용한 DB에 낮은 버전이 뒤늦게 추가되면 실패해야 한다. */
    @Test
    void 낮은_버전의_뒤늦은_추가를_차단한다() throws Exception {
        Files.writeString(migrations.resolve("V1__initial.sql"), "create table sample (id integer primary key);");
        Files.writeString(migrations.resolve("V3__later.sql"), "alter table sample add column note text;");
        Flyway current = flyway("late_version", "filesystem:" + migrations);
        current.migrate();

        Files.writeString(migrations.resolve("V2__late.sql"), "alter table sample add column title text;");
        assertThatThrownBy(current::migrate).isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("Detected resolved migration not applied to database: 2");
    }

    /** 적용을 마친 SQL을 변경하면 체크섬 검증으로 실패해야 한다. */
    @Test
    void 적용한_SQL의_변경을_차단한다() throws Exception {
        Path original = migrations.resolve("V1__initial.sql");
        Files.writeString(original, "create table sample (id integer primary key);");
        Flyway current = flyway("changed_sql", "filesystem:" + migrations);
        current.migrate();

        Files.writeString(original, "create table sample (id bigint primary key);");
        assertThatThrownBy(current::migrate).isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("Migration checksum mismatch for migration version 1");
    }

    /** 가장 높은 적용 버전의 파일도 미래 버전으로 무시하지 않고 삭제를 차단해야 한다. */
    @Test
    void 마지막_적용_SQL의_삭제를_차단한다() throws Exception {
        Files.writeString(migrations.resolve("V1__initial.sql"), "create table sample (id integer primary key);");
        Path latest = migrations.resolve("V2__later.sql");
        Files.writeString(latest, "alter table sample add column note text;");
        Flyway current = flyway("missing_sql", "filesystem:" + migrations);
        current.migrate();

        Files.delete(latest);
        assertThatThrownBy(current::migrate).isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("Detected applied migration not resolved locally: 2");
    }

    /** 검증 대상별 스키마를 분리하고 순서를 건너뛰지 않는 업그레이드 설정을 만든다. */
    private Flyway flyway(String schema, String location) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).locations(location).outOfOrder(false).validateMigrationNaming(true)
                // 기본 *:future 예외를 비워 마지막 적용 파일 삭제도 CI에서 검출한다.
                .ignoreMigrationPatterns(new String[0]).load();
    }
}
