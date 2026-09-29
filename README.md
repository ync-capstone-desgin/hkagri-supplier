# 협력업체 통합관리 시연 프로토타입 (JinsuBae2)

한국농산 캡스톤 디자인 과제의 팀원별 시연 프로토타입이다. 설계 근거는 capstone-docs 저장소의 `docs/JinsuBae2/prototype-design.md`를 따른다.

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security, Flyway |
| 데이터베이스 | PostgreSQL 16 (Docker Compose) |
| 프론트엔드 | React + TypeScript + Ant Design (진행 예정) |

## 실행 방법

사전 준비: Docker Desktop, Java 21

```bash
cp .env.example .env
docker compose up -d
cd backend && ./gradlew bootRun
```

- API 문서: http://localhost:8080/swagger-ui.html
- 기본 프로파일은 `dev`이며, DB가 비어 있으면 가상 샘플 데이터가 자동으로 들어간다.
- DB 초기화: `docker compose down -v` 후 다시 실행

## 시연 계정

비밀번호는 `.env.example`의 `SEED_PASSWORD` 값이다.

| 아이디 | 이름 | 역할 | 권한 |
|---|---|---|---|
| admin | 관리자 | ADMIN | 전체 + 기준정보·시연 도구 |
| manager1 | 김담당 | MANAGER | 조회, 등록·수정 |
| manager2 | 이담당 | MANAGER | 조회, 등록·수정 |
| viewer | 박조회 | VIEWER | 조회만 |

## 구현 현황

| 단계 | 기능 | 요구사항 | 상태 |
|---|---|---|---|
| 1 | 로그인·역할, 협력업체 등록·조회·수정·검색·엑셀 | FR-001~003, FR-013 | 백엔드 완료 |
| 2 | 계약·인증 관리 | FR-004~006, FR-012 | 예정 |
| 3 | 만료 판정·만료 목록·대시보드 | FR-007~009 | 예정 |
| 4 | 알림 대상 생성·발송·이력 | FR-010, FR-011 | 예정 |

## 테스트

```bash
cd backend && ./gradlew test
```

Testcontainers로 실제 PostgreSQL을 띄워 실행하므로 Docker가 켜져 있어야 한다.
