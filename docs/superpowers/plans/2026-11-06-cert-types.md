# 인증 종류 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** ADMIN이 인증 종류를 관리하고 기존 인증 이력을 보존한다.
**Architecture:** 기존 cert_type 테이블을 확장하고 ADMIN API와 인증 목록의 관리 모달을 추가한다. 공통 선택 옵션에서 검색과 입력 용도를 구분한다.
**Tech Stack:** Spring Boot / JPA / PostgreSQL / Flyway, React / Ant Design / TanStack Query.
**Spec:** ../specs/2026-11-06-cert-types-design.md

## Global Constraints
- #36만 구현하고 원본 자료와 실데이터는 업로드하지 않는다.
- 신규 의존성 없이 기존 구조와 권한 정책을 사용한다.
- 기존 인증 종류 코드, 인증 기록, 갱신 연결, 문서 링크를 보존한다.
- 별도 워크트리 feat/#36-cert-types, 기준 origin/develop 25f81d9.

## Review Focus
- 공백·대소문자만 다른 중복 및 비활성 이름 재등록: DB 유일 제약과 API 오류.
- VIEWER·MANAGER 직접 변경 요청: 403 및 데이터 불변.
- 비활성 종류 인증의 메모 수정: 허용, 다른 비활성 종류 선택: 차단.
- 갱신 실패: 기존 인증의 active/renewalStatus와 이력 불변.
- 관리 후 오래된 캐시, 다른 탭·폼 재오픈: 최신 종류와 상태 반영.

### Task 1: 종류 관리 API와 인증 저장 규칙
**Files:** certification/CertType*.java, CertificationService.java, CertificationDtos.java, 새 CertTypeService.java·CertTypeController.java·CertTypeDtos.java, V10__cert_type_management.sql, CertTypeApiTest.java.
**Interfaces:** GET /api/cert-types → {code,name,active}[]; POST /api/admin/cert-types {name}; PUT /api/admin/cert-types/{code} {name}; PATCH /api/admin/cert-types/{code}/active {active}; 변경 API는 단일 종류 반환.
- [x] 실제 API 통합 테스트 추가: 새 종류 사용, 이름 수정 후 기존 인증·갱신 링크·문서 링크 보존, 공백/중복/길이 검증, 권한, 비활성 신규·갱신 차단과 기존 수정 허용, 재활성화.
- [x] `./gradlew test --tests '*CertTypeApiTest'`로 새 엔드포인트 부재에 따른 실패 확인.
- [x] 마이그레이션, 종류 관리 서비스·API, 기존 인증 저장 검증 구현.
- [x] 같은 테스트와 `*ContractApiTest`를 실행해 통과 확인.

### Task 2: 관리 모달과 선택 목록
**Files:** frontend/src/api/{types,certifications}.ts, hooks/useOptions.ts, features/certification/{CertificationListPage,CertificationFormModal,CertTypeManagementModal}.tsx.
**Interfaces:** CertType.active:boolean; createCertType(name), updateCertType(code,name), changeCertTypeActive(code,active); useCertTypeOptions의 입력용 선택 정책.
- [x] 관리자용 관리 모달에 추가·수정·비활성화/재활성화와 오류·로딩 표시 구현.
- [x] 공통 인증 종류 옵션에서 검색은 전체, 입력은 활성 종류와 수정 중인 기존 종류만 제공.
- [x] 갱신은 종류를 재선택할 수 있게 하고 관리 후 관련 목록의 쿼리를 무효화.
- [x] `npm run build`, `npm run lint` 실행.
- [x] 별도 테스트 DB·서버에서 관리→등록→이름 변경→검색→비활성→기존 수정→재활성화 및 비관리자 버튼 숨김을 브라우저로 확인.

### Task 3: 문서·전체 검증·검토
**Files:** docs/cert-types.md, README.md.
- [x] API·권한·스키마·비활성 종류의 동작과 갱신 방법 문서화.
- [x] 백엔드 전체 테스트, `git diff --check`와 최종 변경 검토.
- [x] 권한과 데이터 보존을 중심으로 독립 검토하고 중요한 결함 수정 후 해당 검증 실행.
- [x] 기능 브랜치에 커밋하고 결과·검증·남은 병합 주의점을 사용자에게 보고.

## 실행 결과
- 기존 계약·인증 테스트 8개 통과 후 시작. 새 API 부재(404)로 테스트 실패를 확인하고 구현했다.
- 최종 백엔드 전체 103개 테스트 통과(실패·오류·누락 0). 프론트엔드 빌드 통과, 린트는 기존 10개 경고만 남았다.
- 브라우저: 추가·중복 오류·인증 등록·명칭 수정·검색·비활성·기존 수정·갱신 차단·재활성·갱신·관리자/담당자/조회자 권한 통과.
- 독립 검토의 포커스 갱신과 이름/사용 상태 덮어쓰기 문제를 각각 재현 후 수정했고, 회귀 검증을 통과했다.
- 결정: 이름과 사용 상태 API를 분리해 서로 다른 관리자의 변경이 덮어써지지 않게 했다.
- 앱 워크트리 도구는 중첩 저장소를 지원하지 않아 기존 work 디렉터리의 Git 워크트리를 사용했다.
