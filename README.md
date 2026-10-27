# 한국농산 협력업체 통합관리 시스템 (hkagri-supplier)

영남이공대학교 캡스톤디자인 **404팀**이 한국농산(농업회사법인 한국농산합자회사)과 함께 만드는 협력업체 통합관리 웹 시스템이다.
협력업체의 계약·인증을 한곳에서 관리하고, 만료가 다가오면 담당자에게 미리 알려 갱신 누락을 막는 것이 목표다.

> 이 저장소는 팀원 프로토타입 [prototype-JinsuBae2](https://github.com/ync-capstone-desgin/prototype-JinsuBae2)의 기록을 이어받아 시작했다.
> 기업 담당자 체험용으로 배포한 버전은 그 저장소의 `v0.1-demo` 태그다.

## 팀

| 이름 | GitHub | 역할 |
|---|---|---|
| 배진수 (팀장) | [@JinsuBae2](https://github.com/JinsuBae2) | |
| 박준영 | [@Luluan8975](https://github.com/Luluan8975) | |
| 조대성 | [@Rinhaze](https://github.com/Rinhaze) | |

## 함께 개발하기

**처음이라면 [CONTRIBUTING.md](CONTRIBUTING.md)를 먼저 읽는다.** 이슈 작성부터 PR 머지까지 따라 하기 순서와 규칙이 있다.

```
이슈 작성 → develop에서 브랜치 생성 → 개발·커밋 → 푸시 → PR → 자동 테스트 통과 → 다른 팀원 1명 승인 → 리뷰어가 머지
```

- 기본 브랜치는 `develop`이다. `main`은 배포용이며 `main`·`develop`에 직접 푸시할 수 없다.
- 할 일은 [Issues](https://github.com/ync-capstone-desgin/hkagri-supplier/issues), 일정은 [Milestones](https://github.com/ync-capstone-desgin/hkagri-supplier/milestones)에서 본다.

## 일정 (마일스톤)

| 마일스톤 | 기간 | 내용 |
|---|---|---|
| M1 요구사항 확정 | ~10/23 | 협업 환경 세팅, 요청 기능 완료 조건·방식 확정 |
| M2-1 핵심 기능 | 10/26~11/13 | 업체별 자료보관, 문서·양식 게시판, 회원가입·마이페이지, 메일 알림, 브랜딩 |
| M2-2 확장·반응형 | 11/16~12/4 | 모바일 반응형, 담당자 중간 피드백 반영 |
| M3 최종 시연 | 12/7~12/18 | 통합 테스트, 실무자 검증, 개선, 결과 보고 |

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security, Flyway |
| 데이터베이스 | PostgreSQL 16 (Docker Compose) |
| 알림 | DB 아웃박스 + 정기 발송기, 화면 알림(SSE), 이메일(개발용 Mailpit) |
| 프론트엔드 | React 19 + TypeScript + Vite, Ant Design 6, AG Grid Community, TanStack Query |
| 배포 | AWS EC2 + Docker Compose + Caddy(HTTPS 자동 발급) |
| 협업 | GitHub Issues·PR·Milestones, GitHub Actions(자동 테스트) |

## 내 PC에서 실행하기

사전 준비: Docker Desktop, Java 21, Node.js 20 이상

```bash
cp .env.example .env
docker compose up -d
cd backend && ./gradlew bootRun
```

다른 터미널에서 화면을 실행한다.

```bash
cd frontend && npm install && npm run dev
```

- 화면: http://localhost:5173 (API 요청은 Vite 프록시로 8080에 전달)
- API 문서: http://localhost:8080/swagger-ui.html
- 개발용 메일함(Mailpit): http://localhost:8025 — 알림 메일은 실제로 나가지 않고 여기에 쌓인다
- 기본 프로파일은 `dev`이며, DB가 비어 있으면 가상 샘플 데이터가 자동으로 들어간다.
- DB 초기화: `docker compose down -v` 후 다시 실행
- 올린 첨부파일은 `backend/data/files`에 저장된다 (Git에 올라가지 않음). 지우려면 이 폴더를 삭제한다.
- 시연용 기준일 변경: admin으로 로그인 후 상단 "기준일" 태그를 눌러 날짜를 옮긴다 (dev 프로파일 전용, 서버 재시작 시 실제 날짜로 복귀)

### 시연 계정

비밀번호는 `.env.example`의 `SEED_PASSWORD` 값이다.

| 아이디 | 이름 | 역할 | 권한 |
|---|---|---|---|
| admin | 관리자 | ADMIN | 전체 + 기준정보·시연 도구 |
| manager1 | 김담당 | MANAGER | 조회, 등록·수정 |
| manager2 | 이담당 | MANAGER | 조회, 등록·수정 |
| viewer | 박조회 | VIEWER | 조회만 |

## 구현 현황

### 회원가입 신청

로그인 화면의 **회원가입**에서 이름·아이디·메일·비밀번호·비밀번호 확인과 선택 사항인 가입 사유를 입력한다.
신청은 `PENDING`(승인 대기), `VIEWER`(조회자)로 저장되며 자동 로그인되지 않는다.
관리자 승인 기능은 이슈 #9에서 구현한다. 기존 계정은 `ACTIVE`로 유지된다.
API·입력 규칙·검증 방법은 [docs/signup.md](docs/signup.md)를 참고한다.

프로토타입 단계(v0.1-demo)까지 완료한 기능이다. 이후 기능은 Issues에서 관리한다.

| 기능 | 요구사항 |
|---|---|
| 로그인·역할, 협력업체 등록·조회·수정·검색·엑셀 | FR-001~003, FR-013 |
| 계약·인증 등록·수정·갱신(이력 연결)·처리상태, 업체별 조회, 문서 링크 | FR-004~006, FR-012 |
| 만료 판정(D-day)·만료 예정 목록·대시보드·시연용 기준일 변경 | FR-007~009 |
| 만료 점검·알림 생성(중복 방지)·아웃박스 발송(화면·이메일, 재시도)·알림함·알림 기준 설정 | FR-010, FR-011 |
| 만료 캘린더: 월 달력에 만료일 표시, 날짜별 목록에서 처리상태 변경·갱신 등록 | FR-008 확장 |
| 갱신 현황판: 처리상태별 칸에 카드를 끌어 놓아 상태 변경, 완료 칸에 놓으면 갱신 등록 | FR-006 확장 |

## 문서

| 문서 | 내용 |
|---|---|
| [CONTRIBUTING.md](CONTRIBUTING.md) | 협업 규칙과 따라 하기 (이슈·브랜치·커밋·PR·리뷰) |
| [docs/user-guide.md](docs/user-guide.md) | 메뉴별 사용 설명서 (스크린샷 포함) |
| [docs/deploy-aws.md](docs/deploy-aws.md) | AWS EC2 배포 가이드 |
| [capstone-docs](https://github.com/ync-capstone-desgin/capstone-docs) | 기획서, 설계, 미팅 자료 |

## 테스트

```bash
cd backend && ./gradlew test
```

Testcontainers로 실제 PostgreSQL을 띄워 실행하므로 Docker가 켜져 있어야 한다. (총 57건)
PR을 올리면 GitHub Actions가 같은 테스트와 화면 빌드를 자동으로 실행한다.

| 테스트 | 확인 내용 |
|---|---|
| SupplierApiTest | VT-001 정상 등록, VT-002 필수값 누락, 역할별 권한 |
| ContractApiTest | VT-003 종료일 < 시작일, VT-007 업체별 조회, 갱신 이력, 중복 경고, 갱신완료 직접 지정·이력 건 상태 변경 차단 |
| ExpiryPolicyTest | VT-004 오늘 만료, VT-005 7·30·90일 경계 |
| ExpiryApiTest | VT-008 만료 목록 필터, VT-009 대시보드 = 목록 건수, 만료 캘린더 기간 조회·기간 제한, 갱신 현황판 대상 |
| NotificationScanTest | VT-006 중복 알림 방지, 가장 작은 기준일 적용, 제외 대상, 발송 실패 재시도, 갱신 시 자동 완료 |
| FileApiTest | 첨부파일 같은 이름·내용으로 내려받기, HWP 허용, 형식·서명·크기(20MB) 거절, 조회자 권한, 삭제 후 숨김과 삭제 이력 |
| FileServiceTest | 업로드 실패·롤백 시 남는 파일 정리, 동시 삭제 시 한 명만 성공하고 처음 삭제 기록 유지 |
| LocalDiskFileStorageTest | 디스크 저장·읽기·지우기, 저장 폴더 밖 경로 거절, 덮어쓰기 방지, 쓰다 실패한 반쪽 파일 정리 |
