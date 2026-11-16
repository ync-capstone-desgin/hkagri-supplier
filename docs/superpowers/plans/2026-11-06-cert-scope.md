# 인증 적용 대상 입력·검색 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 담당자가 인증 적용 대상을 직접 입력하고 검색·갱신·만료 안내에서 구분한다.
**Architecture:** 기존 인증에 nullable `scope` 텍스트 하나를 추가한다. 공통 인증 표시명은 종류와 적용 대상을 조합해 만료 목록·캘린더·갱신 보드·알림에서 재사용한다.
**Tech Stack:** Spring Boot/JPA/PostgreSQL/Flyway, React/TypeScript/Ant Design/AG Grid.
**Spec:** https://github.com/ync-capstone-desgin/hkagri-supplier/issues/37

## Global Constraints

- 선택 입력, 최대 200자, 앞뒤 공백 제거, 미입력·공백은 null. 기존 인증은 그대로 null.
- 실제 기업 자료 추론·등록 없이 가상 데이터로 검증한다.
- 기존 역할 권한·검색 필터·페이지네이션·갱신 이력을 유지한다.
- PR #38의 `f0466b8`에서 분기한다. V9는 #35, V10은 #38이 사용하므로 새 마이그레이션은 V11이다.
- 기존 개발 워크트리와 미리보기 5175/8081을 재사용한다. 새 서버 쌍을 추가하지 않는다.

## Review Focus

- 누락·null·빈 문자열·공백 입력과 기존 기록: 조회·수정·갱신 및 자연스러운 표시.
- 200자 경계와 초과 입력: 200자는 저장, 초과는 필드 오류.
- 대상 키워드와 기존 필터·페이지 조합: 다른 업체·상태 데이터가 섞이지 않음.
- 갱신 기본값과 명시적 변경·삭제: 이전 기록 불변.
- VIEWER: 대상 조회 가능, 등록·수정·갱신 차단.

## Task 1: 저장·검색·갱신과 안내

**Files:** `certification/Certification{,Dtos,Service,Repository,Controller}.java`, `db/migration/V11__certification_scope.sql`, `expiry/ExpiryService.java`, `notification/job/NotificationScanService.java`, `file/FileDtos.java`; 엔티티 update 호출부와 테스트.
**Interfaces:** 요청 `scope?: string | null`, 응답 `scope: string | null`; `Certification.getDisplayName()`은 종류 또는 `종류 · 적용 대상`을 반환한다. 엑셀에는 독립 적용 대상 열을 둔다.

- [x] `CertificationScopeApiTest`에 저장·경계·누락·필터·페이지·갱신·권한·만료 안내·알림·엑셀 검증을 작성한다.
- [x] `./gradlew test --tests '*CertificationScopeApiTest'` 실행: 새 기능 부재로 실패 확인.
- [x] V11 컬럼과 DTO/엔티티/검색/표시명/엑셀을 구현하고 호출부를 갱신한다.
- [x] 같은 테스트 실행: 모두 통과.

## Task 2: 입력·목록·갱신 화면

**Files:** `api/types.ts`, `CertificationFormModal.tsx`, `CertificationGrid.tsx`, `CertificationListPage.tsx`, `SupplierFilesTab.tsx`.

- [x] 등록·수정에 선택 입력(예: 공장, 잼류, 제품 A)을 추가하고 갱신 초기값으로 복사한다.
- [x] 공유 인증 그리드의 적용 대상 열과 검색 안내, 파일 연결 선택명에 반영한다.
- [x] Node 24 `npm run build`, `npm run lint`: 빌드 통과·신규 오류 없음.
- [x] 실제 Chrome에서 등록→대상 검색→업체별 목록→갱신 기본값/수정→이전 이력, 만료 화면, VIEWER 표시를 확인한다.

## Task 3: 문서·최종 검증

- [x] `docs/cert-scope.md`에 API·DB·화면·마이그레이션 순서를 문서화하고 README/사용 설명서를 연결한다.
- [x] 백엔드 전체 테스트와 최종 diff 검토로 회귀·권한·기존 빈 값 호환성을 확인한다.
- [x] 기존 서버를 교체해 미리보기를 제공하고 검증용 자료를 정리한다.
- [x] 별도 커밋·브랜치로 작업 결과를 보존한다. PR은 #38 위의 변경만 검토할 수 있도록 분리한다.

## 진행 기록

- 기준 커밋: `f0466b8`. 기존 전체 테스트 109개와 GitHub CI 통과 결과를 기준선으로 재사용한다.
- 200자 길이와 `scope` API 이름은 기존 입력·데이터 구조에 맞춘 구현 선택이다.
- Ruling: 최대 길이 적용 대상이 알림 제목의 기존 200자 제한을 초과하는 통합 테스트 실패를 재현했다. V11에서 제목 500자·본문 1000자로 확대하고 각 입력의 최대 길이를 합친 알림 저장을 검증한다. 원문을 잘라 대상 구분 정보를 잃지 않기 위한 호환 변경이다.

- 저장·안내 검증: 새 통합 테스트 11개 통과. 전체 회귀 120개 통과. 프런트 빌드 통과, 린트 오류 0/기존 경고 10개.

- 브라우저 검증: 등록·검색·업체별 조회·만료 3화면·갱신 기본값·이전 이력·대상 삭제·조회자 권한 통과, 브라우저 오류 없음. 기존 미리보기 인증 34건 보존과 scope 전체 null을 확인했다.
- Ruling: CI는 develop/main 대상 PR에서 실행되므로 새 PR의 base는 develop으로 설정한다. 선행 PR #38을 먼저 병합하면 이 PR에는 #37 변경만 남는다. #38 변경을 별도로 다시 병합하지 않는다.

- 최종 diff 자체 검토: 요청/응답/DB/검색/권한/표시명/갱신 복사와 모든 호출부를 확인했다. 새 의존성이나 기업 원본은 포함하지 않았다. 브라우저 검증 자료는 정리했고 기존 인증 34건이 유지된다.
