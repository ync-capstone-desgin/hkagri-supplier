# 협력업체 통합관리 API

기본 경로는 `/api`다. 개발 서버에서 전체 요청 형식과 응답 모델을 펼쳐 보거나 직접 호출할 수 있다. [Swagger UI](http://localhost:8080/swagger-ui.html) · [OpenAPI JSON](http://localhost:8080/v3/api-docs)

## 세션과 권한

로그인에 성공하면 서버가 세션 쿠키를 발급한다. 브라우저는 이후 같은 출처의 요청에 쿠키를 보낸다. 표의 **로그인**은 활성 계정의 모든 역할(`VIEWER`, `MANAGER`, `ADMIN`), **편집**은 `MANAGER` 또는 `ADMIN`, **관리자**는 `ADMIN`만을 뜻한다. 관리자용 조회도 관리자 권한이 필요하다. [보안 규칙](../backend/src/main/java/com/hankooknongsan/supplier/auth/SecurityConfig.java)

- 마이페이지는 본인 계정만 다룬다. 내 알림 조회·확인 처리는 조회자도 가능하다. 다른 수신자의 알림은 변경할 수 없다.
- 소통 메모는 편집 권한이 있어도 작성자 본인이나 관리자만 삭제할 수 있다. 업체·계약·인증 복원은 관리자만 가능하다.
- 시연용 기준일 변경과 만료 점검 수동 실행은 `dev` 프로파일에만 등록된다. 로그아웃은 Spring Security가 처리하므로 컨트롤러 기반 Swagger 목록에서 빠진다.

## 호출 예시

**세션 확인.** 로그인 요청은 `POST /api/auth/login`에 `username`과 `password`를 JSON으로 보낸다. 로그인 후 같은 세션에서 `GET /api/auth/me`를 호출한 응답이다.

```json
{
  "id": 1,
  "username": "admin",
  "name": "관리자",
  "role": "ADMIN"
}
```

**목록 검색.** `GET /api/suppliers?keyword=DOC15-NO-MATCH&page=0&size=10`의 검색 결과가 없을 때도 200을 반환한다. 페이지 번호는 0부터 시작한다.

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0
}
```

**담당자 등록.** 편집 권한으로 `POST /api/suppliers/12/contacts`에 아래 JSON을 보낸다. 이름만 필수다.

```json
{
  "name": "문서 시연",
  "title": "담당자"
}
```

성공하면 201과 등록된 담당자를 반환한다.

```json
{
  "id": 1,
  "name": "문서 시연",
  "title": "담당자",
  "phone": null,
  "email": null
}
```

**업무별 조회 예시.** 아래는 같은 로컬 시연 DB에서 로그인 후 확인한 응답이다. 날짜·건수는 시연 데이터에 따라 달라진다.

| 요청 | 응답 예시 |
|---|---|
| `GET /api/contracts/files/counts?ids=1,2` | `{}` (첨부가 없는 계약은 키가 생기지 않는다) |
| `GET /api/forms/category-counts` | `{"CONTRACT":0,"CHECKLIST":0,"OTHER":0}` |
| `GET /api/system/clock` | `{"today":"2026-11-17","shifted":false,"demoClock":true,"horizonDays":90}` |
| `GET /api/expiries/calendar?from=2035-01-01&to=2035-01-10` | `[]` (해당 기간의 만료 항목 없음) |
| `GET /api/notifications/unread-count` | `{"count":0}` |

## 오류 응답

같은 등록 요청에서 `name`을 빈 문자열로 보내면 400과 다음 ProblemDetail을 반환한다. 화면에서 입력칸별 메시지를 표시할 때는 선택 필드인 `errors`를 사용한다.

```json
{
  "detail": "입력값을 확인해 주세요.",
  "instance": "/api/suppliers/12/contacts",
  "status": 400,
  "title": "Bad Request",
  "errors": [
    {
      "field": "name",
      "message": "이름을 입력해 주세요."
    }
  ]
}
```

| 상태 | 처리 기준 |
|---|---|
| 400 | 입력 제약·JSON 형식·업무 규칙 위반. `detail`을 표시하고 `errors`가 있으면 해당 필드에 연결한다. |
| 401 | 로그인 실패 또는 세션 만료. 보호된 API의 익명 요청은 보안 필터가 처리하므로 응답 본문을 전제로 하지 않는다. |
| 403 | 역할 또는 대상에 대한 접근 권한 부족. 보안 필터와 서비스의 응답 형식이 다를 수 있다. |
| 404 | 조회 대상을 찾을 수 없거나 해당 화면에서 접근할 수 없는 삭제 데이터. |
| 409 | 고유값 충돌 등 해당 서비스가 충돌로 처리하는 경우. |
| 413 | 업로드 용량 초과. 서버 설정과 전송 시점에 따라 클라이언트에서 연결 오류로 보일 수 있다. |

## 기능별 엔드포인트

`{id}` 등 중괄호는 경로 변수다. 요청 열의 JSON 필드는 키 목록이며 필수 여부·길이·자료형은 해당 요청 DTO를 따른다. 응답 열의 이름은 서버 반환 DTO다. `PageResponse<T>`는 위 목록 예시의 구조이고 `List<T>`는 JSON 배열이다. 파일 다운로드와 SSE는 JSON 응답이 아니다.

### 회원·세션·담당자

| 메서드와 경로 | 권한 | 기능 | 요청 | 성공 응답 |
|---|---|---|---|---|
| `GET /api/admin/users` | 관리자 | 전체 사용자 목록을 읽는다 | — | 200 · `UserList` |
| `POST /api/admin/users/{id}/actions` | 관리자 | 승인·거절·역할 변경·비활성화·재사용을 처리한다 | JSON `action`, `role` | 200 · `UserItem` |
| `POST /api/auth/login` | 공개 | 아이디·비밀번호로 로그인하고, 성공하면 세션에 로그인 정보를 저장한다 | JSON `username`, `password` | 200 · `Me` |
| `GET /api/auth/me` | 로그인 | 현재 로그인한 사용자 정보를 반환한다 | — | 200 · `Me` |
| `POST /api/auth/signup` | 공개 | 유효한 신청을 승인 대기로 저장하고 비밀번호 등 사용자 정보는 반환하지 않는다 | JSON `name`, `username`, `email`, `password`, `passwordConfirm`, `signupReason` | 201 · 본문 없음 |
| `PUT /api/me/password` | 로그인 | 비밀번호를 바꾼다 | JSON `currentPassword`, `newPassword` | 200 · 본문 없음 |
| `GET /api/me/profile` | 로그인 | 내 정보를 조회한다 | — | 200 · `MyProfile` |
| `PUT /api/me/profile` | 로그인 | 이름·이메일·알림메일 수신 여부를 수정한다 | JSON `name`, `email`, `notifyEmail` | 200 · `MyProfile` |
| `GET /api/users` | 로그인 | 담당자 선택용 활성 계정 목록을 반환한다 | — | 200 · `List<UserOption>` |
| `POST /api/auth/logout` | 공개 | 현재 세션을 종료한다 (보안 필터) | — | 200 · 본문 없음 |

### 협력업체·소통·변경 이력

| 메서드와 경로 | 권한 | 기능 | 요청 | 성공 응답 |
|---|---|---|---|---|
| `GET /api/admin/changes` | 관리자 | 전체 변경 이력을 대상 종류와 검색어(업체명·대상 이름·작업자)로 검색한다 | 쿼리 `targetType`, `keyword`, `page`, `size` | 200 · `PageResponse<ChangeItem>` |
| `GET /api/suppliers` | 로그인 | 협력업체 목록을 검색한다 | 쿼리 `keyword`, `status`, `certType`, `missingDocs`, `certExpired`, `page`, `size` | 200 · `PageResponse<SupplierListItem>` |
| `POST /api/suppliers` | 편집 | 협력업체를 등록한다 | JSON `name`, `businessNumber`, `status`, `contactName`, `contactPhone`, `contactEmail`, `ownerId`, `memo` | 201 · `SupplierDetail` |
| `GET /api/suppliers/excel` | 로그인 | 현재 검색조건의 협력업체 목록을 엑셀 파일로 내려받는다 | 쿼리 `keyword`, `status`, `certType`, `missingDocs`, `certExpired` | 200 · XLSX 파일 |
| `GET /api/suppliers/{id}` | 로그인 | 협력업체 상세정보를 조회한다 | — | 200 · `SupplierDetail` |
| `PUT /api/suppliers/{id}` | 편집 | 협력업체 기본정보를 수정한다 | JSON `name`, `businessNumber`, `status`, `contactName`, `contactPhone`, `contactEmail`, `ownerId`, `memo` | 200 · `SupplierDetail` |
| `DELETE /api/suppliers/{id}` | 편집 | 협력업체를 삭제한다 | — | 204 · 본문 없음 |
| `POST /api/suppliers/{id}/restore` | 관리자 | 삭제한 협력업체를 되살린다 | — | 204 · 본문 없음 |
| `GET /api/suppliers/{supplierId}/changes` | 로그인 | 한 업체와 그 업체의 계약·인증 변경 이력을 최신순으로 조회한다 | 쿼리 `page`, `size` | 200 · `PageResponse<ChangeItem>` |
| `GET /api/suppliers/{supplierId}/contacts` | 로그인 | 업체의 추가 담당자 목록을 조회한다 | — | 200 · `List<ContactItem>` |
| `POST /api/suppliers/{supplierId}/contacts` | 편집 | 업체에 담당자를 추가한다 | JSON `name`, `title`, `phone`, `email` | 201 · `ContactItem` |
| `PUT /api/suppliers/{supplierId}/contacts/{contactId}` | 편집 | 담당자 정보를 수정한다 | JSON `name`, `title`, `phone`, `email` | 200 · `ContactItem` |
| `DELETE /api/suppliers/{supplierId}/contacts/{contactId}` | 편집 | 담당자를 삭제한다 | — | 204 · 본문 없음 |
| `GET /api/suppliers/{supplierId}/notes` | 로그인 | 업체의 소통 메모를 소통한 날짜가 최근인 순으로 조회한다 | 쿼리 `page`, `size` | 200 · `PageResponse<NoteItem>` |
| `POST /api/suppliers/{supplierId}/notes` | 편집 | 소통 메모를 남긴다 | JSON `contactDate`, `content` | 201 · `NoteItem` |
| `DELETE /api/suppliers/{supplierId}/notes/{noteId}` | 편집 | 소통 메모를 삭제한다 | — | 204 · 본문 없음 |

### 계약·인증과 인증 종류

| 메서드와 경로 | 권한 | 기능 | 요청 | 성공 응답 |
|---|---|---|---|---|
| `POST /api/admin/cert-types` | 관리자 | 관리자만 새 인증 종류를 추가할 수 있다 | JSON `name` | 201 · `CertTypeItem` |
| `PUT /api/admin/cert-types/{code}` | 관리자 | 사용 여부와 독립적으로 이름을 수정한다 | JSON `name` | 200 · `CertTypeItem` |
| `PATCH /api/admin/cert-types/{code}/active` | 관리자 | 기존 인증 기록은 유지하고 신규 선택 가능 여부를 변경한다 | JSON `active` | 200 · `CertTypeItem` |
| `PATCH /api/admin/cert-types/{code}/required` | 관리자 | 인증 종류를 필수 인증으로 정하거나 해제한다 | JSON `required` | 200 · `CertTypeItem` |
| `GET /api/cert-types` | 로그인 | 인증 종류 목록을 조회한다 | — | 200 · `List<CertTypeItem>` |
| `GET /api/certifications` | 로그인 | 인증 목록을 검색한다 | 쿼리 `supplierId`, `keyword`, `certType`, `ownerId`, `renewalStatus`, `includeInactive`, `page`, `size` | 200 · `PageResponse<CertificationItem>` |
| `POST /api/certifications` | 편집 | 인증을 등록한다 | JSON `supplierId`, `certTypeCode`, `certificateNumber`, `scope`, `issuedDate`, `expiryDate`, `ownerId`, `documentUrl`, `memo` | 201 · `Saved` |
| `GET /api/certifications/excel` | 로그인 | 현재 검색조건의 인증 목록을 엑셀 파일로 내려받는다 | 쿼리 `supplierId`, `keyword`, `certType`, `ownerId`, `renewalStatus`, `includeInactive` | 200 · XLSX 파일 |
| `GET /api/certifications/{id}` | 로그인 | 인증 한 건을 조회한다 | — | 200 · `CertificationItem` |
| `PUT /api/certifications/{id}` | 편집 | 인증 정보를 수정한다 | JSON `supplierId`, `certTypeCode`, `certificateNumber`, `scope`, `issuedDate`, `expiryDate`, `ownerId`, `documentUrl`, `memo` | 200 · `Saved` |
| `DELETE /api/certifications/{id}` | 편집 | 인증을 삭제한다 | — | 204 · 본문 없음 |
| `POST /api/certifications/{id}/renew` | 편집 | 인증을 갱신한다: 새 인증을 등록하고 기존 인증은 갱신완료·비활성으로 바꾼다 | JSON `supplierId`, `certTypeCode`, `certificateNumber`, `scope`, `issuedDate`, `expiryDate`, `ownerId`, `documentUrl`, `memo` | 201 · `Saved` |
| `PATCH /api/certifications/{id}/renewal-status` | 편집 | 갱신 처리상태만 바꾼다 | JSON `renewalStatus` | 200 · `CertificationItem` |
| `POST /api/certifications/{id}/restore` | 관리자 | 삭제한 인증을 되살린다 | — | 204 · 본문 없음 |
| `GET /api/contracts` | 로그인 | 계약 목록을 검색한다 | 쿼리 `supplierId`, `keyword`, `ownerId`, `renewalStatus`, `includeInactive`, `page`, `size` | 200 · `PageResponse<ContractItem>` |
| `POST /api/contracts` | 편집 | 계약을 등록한다 | JSON `supplierId`, `contractNumber`, `title`, `startDate`, `endDate`, `autoRenew`, `ownerId`, `documentUrl`, `memo` | 201 · `Saved` |
| `GET /api/contracts/excel` | 로그인 | 현재 검색조건의 계약 목록을 엑셀 파일로 내려받는다 | 쿼리 `supplierId`, `keyword`, `ownerId`, `renewalStatus`, `includeInactive` | 200 · XLSX 파일 |
| `GET /api/contracts/{id}` | 로그인 | 계약 한 건을 조회한다 | — | 200 · `ContractItem` |
| `PUT /api/contracts/{id}` | 편집 | 계약 정보를 수정한다 | JSON `supplierId`, `contractNumber`, `title`, `startDate`, `endDate`, `autoRenew`, `ownerId`, `documentUrl`, `memo` | 200 · `Saved` |
| `DELETE /api/contracts/{id}` | 편집 | 계약을 삭제한다 | — | 204 · 본문 없음 |
| `POST /api/contracts/{id}/renew` | 편집 | 계약을 갱신한다: 새 계약을 등록하고 기존 계약은 갱신완료·비활성으로 바꾼다 | JSON `supplierId`, `contractNumber`, `title`, `startDate`, `endDate`, `autoRenew`, `ownerId`, `documentUrl`, `memo` | 201 · `Saved` |
| `PATCH /api/contracts/{id}/renewal-status` | 편집 | 갱신 처리상태만 바꾼다 | JSON `renewalStatus` | 200 · `ContractItem` |
| `POST /api/contracts/{id}/restore` | 관리자 | 삭제한 계약을 되살린다 | — | 204 · 본문 없음 |

### 파일·업체 자료·필수 서류

| 메서드와 경로 | 권한 | 기능 | 요청 | 성공 응답 |
|---|---|---|---|---|
| `POST /api/admin/file-categories` | 관리자 | 새 자료 종류를 추가한다 | JSON `name` | 201 · `CategoryItem` |
| `PUT /api/admin/file-categories/{id}` | 관리자 | 자료 종류의 이름을 바꾼다 | JSON `name` | 200 · `CategoryItem` |
| `PATCH /api/admin/file-categories/{id}/active` | 관리자 | 자료 종류를 사용 중지하거나 다시 사용한다 | JSON `active` | 200 · `CategoryItem` |
| `PATCH /api/admin/file-categories/{id}/required` | 관리자 | 자료 종류를 필수 서류로 정하거나 해제한다 | JSON `required` | 200 · `CategoryItem` |
| `GET /api/admin/files/deleted` | 관리자 | 삭제한 파일 목록(삭제 이력)을 최근에 지운 순으로 조회한다 | 쿼리 `page`, `size` | 200 · `PageResponse<DeletedFile>` |
| `GET /api/certifications/files/counts` | 로그인 | 목록 화면에서 첨부 아이콘을 한 번에 그리기 위한, 인증별 첨부 개수 | 쿼리 `ids (필수)` | 200 · `Map<Long, Long>` |
| `GET /api/certifications/{certificationId}/files` | 로그인 | 인증 한 건에 연결된 첨부파일 목록 (최신순) | — | 200 · `List<FileInfo>` |
| `GET /api/contracts/files/counts` | 로그인 | 목록 화면에서 첨부 아이콘을 한 번에 그리기 위한, 계약별 첨부 개수 | 쿼리 `ids (필수)` | 200 · `Map<Long, Long>` |
| `GET /api/contracts/{contractId}/files` | 로그인 | 계약 한 건에 연결된 첨부파일 목록 (최신순) | — | 200 · `List<FileInfo>` |
| `GET /api/file-categories` | 로그인 | 전체 자료 종류를 표시 순서대로 조회한다 | — | 200 · `List<CategoryItem>` |
| `POST /api/files` | 편집 | 파일 하나를 올린다 | 멀티파트 `file` | 201 · `FileInfo` |
| `GET /api/files/search` | 로그인 | 전체 자료를 검색한다 | 쿼리 `keyword`, `categoryId`, `page`, `size` | 200 · `PageResponse<FileSearchItem>` |
| `GET /api/files/{id}` | 로그인 | 파일 정보(이름·크기·올린 사람)를 조회한다 | — | 200 · `FileInfo` |
| `DELETE /api/files/{id}` | 편집 | 파일을 삭제한다 | — | 204 · 본문 없음 |
| `PATCH /api/files/{id}/category` | 편집 | 이미 올린 파일의 자료 종류를 바꾼다 | JSON `categoryId` | 200 · `SupplierFile` |
| `GET /api/files/{id}/download` | 로그인 | 파일을 원래 이름으로 내려받는다 | — | 200 · 파일 바이트 |
| `GET /api/suppliers/{supplierId}/files` | 로그인 | 업체의 자료를 페이지별로 조회한다 | 쿼리 `categoryId`, `page`, `size` | 200 · `PageResponse<SupplierFile>` |
| `POST /api/suppliers/{supplierId}/files` | 편집 | 파일 하나를 업체 또는 업체의 계약·인증에 연결한다 | 쿼리 `contractId`, `certificationId`, `categoryId` · 멀티파트 `file` | 201 · `SupplierFile` |
| `GET /api/suppliers/{supplierId}/required-documents` | 로그인 | 한 업체의 필수 서류별 제출 여부를 조회한다 | — | 200 · `List<RequiredDocument>` |

### 양식 자료실

| 메서드와 경로 | 권한 | 기능 | 요청 | 성공 응답 |
|---|---|---|---|---|
| `POST /api/form-folders` | 편집 | 부모 아래 새 폴더를 만든다 | JSON `parentId`, `name` | 201 · `FolderItem` |
| `GET /api/form-folders/{id}` | 로그인 | 현재 폴더의 경로와 하위 폴더를 조회한다 | — | 200 · `FolderView` |
| `PUT /api/form-folders/{id}` | 편집 | 폴더 이름을 바꾼다 | JSON `name` | 200 · `FolderItem` |
| `DELETE /api/form-folders/{id}` | 편집 | 빈 폴더만 삭제하고 삭제자를 남긴다 | — | 204 · 본문 없음 |
| `GET /api/forms` | 로그인 | 제목·분류로 게시글을 검색한다 | 쿼리 `keyword`, `category`, `folderId`, `page`, `size` | 200 · `PageResponse<PostItem>` |
| `POST /api/forms` | 편집 | JSON post와 여러 files를 함께 저장한다 | 멀티파트 `post`(JSON), `files`(선택·반복) | 201 · `PostDetail` |
| `GET /api/forms/category-counts` | 로그인 | 분류 탭에 붙일 개수를 현재 폴더·검색어 기준으로 조회한다 | 쿼리 `keyword`, `folderId` | 200 · `Map<FormCategory, Long>` |
| `GET /api/forms/{id}` | 로그인 | 게시글 상세와 첨부 정보를 조회한다 | — | 200 · `PostDetail` |
| `PUT /api/forms/{id}` | 편집 | 게시글 수정과 첨부 교체를 함께 반영한다 | 멀티파트 `post`(JSON), `files`(선택·반복) | 200 · `PostDetail` |
| `DELETE /api/forms/{id}` | 편집 | 게시글과 첨부를 삭제하고 감사 이력을 보존한다 | — | 204 · 본문 없음 |

### 대시보드·만료 조회·공통 코드

| 메서드와 경로 | 권한 | 기능 | 요청 | 성공 응답 |
|---|---|---|---|---|
| `GET /api/codes` | 로그인 | 코드 그룹별 코드 목록을 반환한다 | — | 200 · `Map<String, List<Code>>` |
| `GET /api/dashboard/summary` | 로그인 | 로그인 사용자의 대시보드 요약(카드 숫자, 급한 항목, 내 담당 건수)을 반환한다 | — | 200 · `DashboardSummary` |
| `GET /api/expiries` | 로그인 | 만료 예정 목록을 조회한다 | 쿼리 `period`, `targetType`, `ownerId`, `renewalStatus` | 200 · `List<ExpiryItem>` |
| `GET /api/expiries/board` | 로그인 | 갱신 현황판용 카드 목록을 조회한다 | 쿼리 `targetType`, `ownerId` | 200 · `List<ExpiryItem>` |
| `GET /api/expiries/calendar` | 로그인 | 만료 캘린더용으로 기간 안에 만료되는 계약·인증을 날짜순으로 조회한다 | 쿼리 `from (필수)`, `to (필수)`, `targetType`, `ownerId`, `renewalStatus` | 200 · `List<ExpiryItem>` |
| `GET /api/expiries/excel` | 로그인 | 현재 조건의 만료 예정 목록을 엑셀 파일로 내려받는다 | 쿼리 `period`, `targetType`, `ownerId`, `renewalStatus` | 200 · XLSX 파일 |
| `GET /api/system/clock` | 로그인 | 현재 기준일과 시연용 기준일 변경 가능 여부를 반환한다 | — | 200 · `ClockInfo` |

### 알림·설정·시연 도구

| 메서드와 경로 | 권한 | 기능 | 요청 | 성공 응답 |
|---|---|---|---|---|
| `PUT /api/admin/clock` | 관리자 | 기준일을 지정한 날짜로 옮기거나(null이면) 실제 날짜로 되돌린다 (dev 전용) | JSON `date` | 200 · `ClockInfo` |
| `GET /api/admin/deliveries` | 관리자 | 최근 발송 기록 200건을 조회한다 | — | 200 · `List<DeliveryLogItem>` |
| `POST /api/admin/jobs/expiry-scan` | 관리자 | 현재 기준일로 만료 점검을 실행하고, 생성된 알림을 바로 발송한다 (dev 전용) | — | 200 · `RunResult` |
| `GET /api/notifications` | 로그인 | 내 알림함을 만료일이 가까운 순으로 조회한다 | 쿼리 `actionStatus`, `page`, `size` | 200 · `PageResponse<NotificationItem>` |
| `POST /api/notifications/read-all` | 로그인 | 내 미확인 알림을 모두 확인 처리한다 | — | 200 · `Map<String, Integer>` |
| `GET /api/notifications/stream` | 로그인 | 실시간 알림 연결을 연다 | — | 200 · SSE 스트림 |
| `GET /api/notifications/unread-count` | 로그인 | 내 미확인 알림 수를 반환한다 | — | 200 · `Map<String, Long>` |
| `PATCH /api/notifications/{id}/action` | 로그인 | 알림의 처리상태를 바꾼다 | JSON `actionStatus` | 200 · `NotificationItem` |
| `GET /api/settings/notification-rules` | 로그인 | 알림 기준 목록을 조회한다 | — | 200 · `List<RuleItem>` |
| `PUT /api/settings/notification-rules/{id}` | 관리자 | 알림 기준을 바꾼다 | JSON `days`, `channels`, `enabled` | 200 · `RuleItem` |

파일 첨부 예시와 자료실 멀티파트 파트의 Content-Type은 기능별 명세에 정리했다. [업체 자료](supplier-files.md) · [양식 자료실](forms-api.md)

API를 추가·변경하는 PR에서는 이 표의 메서드·경로·역할을 컨트롤러와 보안 설정에 맞춰 갱신한다. 원래 설계의 API 범위는 4.4절에서 확인할 수 있다. [프로토타입 설계](https://github.com/ync-capstone-desgin/capstone-docs/blob/main/docs/team/reference/prototype-design.md)
