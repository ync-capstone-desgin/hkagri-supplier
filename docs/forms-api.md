# 사내 양식 자료실 API (#6·#7, FR-022)

업체와 무관한 공용 양식을 폴더 안의 게시글과 여러 첨부로 관리한다. [폴더형 화면 사용법](forms-ui.md)은 #7에서 연결한다.

## 권한과 저장 규칙

- 로그인한 사용자는 목록·상세·첨부 다운로드를 이용한다.
- ADMIN·MANAGER는 게시글과 폴더를 등록·수정·삭제한다. 작성자에 한정하지 않고 공동 관리한다. 기업 질문 Q-10 답변 전 기본 권한이다.
- 게시글과 모든 새 첨부는 한 트랜잭션이다. 한 파일이라도 실패하면 게시글 변경, 첨부 삭제, 새 파일 저장을 모두 취소한다.
- 파일 내용은 기존 로컬 저장소, 메타데이터는 `stored_file`에 보관한다. 기존 파일 ID로 다른 업무의 파일을 연결하는 기능은 없다.
- 게시글 삭제 시 첨부도 논리 삭제한다. 파일 ID를 알아도 다운로드는 404다. DB 감사 기록과 디스크 내용은 남는다.
- 파일 없는 게시글도 가능하다. 기존 파일 정책(기본 파일당 20MB, 요청 전체 21MB)이 적용된다. 다중 첨부의 합계와 multipart 헤더까지 요청 크기에 포함되므로 큰 묶음이 필요하면 `FILE_MAX_REQUEST_SIZE`를 조정한다.

## 엔드포인트

| 요청 | 기능 | 응답 |
|---|---|---|
| GET `/api/forms?folderId=1&keyword=&category=&page=0&size=20` | 현재 폴더의 제목 검색·분류 필터·최신순 페이지 | 200, `{content, page, size, totalElements}` |
| GET `/api/forms/{id}` | 게시글 상세 및 살아 있는 첨부 | 200 |
| POST `/api/forms` | 게시글·첨부 등록 | 201, 상세 |
| PUT `/api/forms/{id}` | 제목·분류·설명 변경 및 첨부 추가/삭제 | 200, 상세 |
| DELETE `/api/forms/{id}` | 게시글·첨부 논리 삭제 | 204 |
| GET `/api/files/{id}/download` | 기존 API로 원본 다운로드 | 200, 파일 |
| GET `/api/form-folders/{id}` | 현재 폴더·최상위부터 경로·직접 하위 폴더 | 200, `{folder, path, children}` |
| POST `/api/form-folders` | `{parentId, name}`으로 하위 폴더 생성 | 201, 폴더 |
| PUT `/api/form-folders/{id}` | `{name}`으로 이름 변경 | 200, 폴더 |
| DELETE `/api/form-folders/{id}` | 빈 폴더 논리 삭제 | 204 |

최상위 폴더 ID는 `1`이며 이름 변경·삭제할 수 없다. 하위 폴더는 깊이 제한 없이 생성하며 이동 기능은 없다. `path`는 최상위부터 현재 폴더까지, `children`은 이름순 직접 하위 폴더다. 폴더 응답은 `{id, parentId, name, updatedAt, updatedBy}`이고 최상위의 `parentId`는 null이다.

폴더 이름은 앞뒤 공백 제거·NFC 정규화 후 1~100자다. `.`·`..`·경로 구분자(`/`, `\`)·제어문자는 거절한다. 같은 부모 아래 대소문자가 다른 동일 이름도 거절하며, 다른 부모 아래에는 같은 이름을 쓸 수 있다. 삭제되지 않은 하위 폴더나 양식이 하나라도 있으면 폴더 삭제는 400이다. 첨부 없는 양식도 포함한다.

양식 목록에서 `folderId`를 생략하면 기존처럼 전체 폴더를 검색한다. 지정하면 바로 그 폴더만 검색하며 하위 폴더의 양식은 합치지 않는다. 삭제된 폴더 조회·생성 대상으로 사용은 404다.

분류: `CONTRACT`(계약 양식), `CHECKLIST`(점검표), `OTHER`(기타).
제목: 공백만 입력 불가·최대 200자. 설명: 선택·최대 5000자. 페이지: 0 이상, 크기: 1~100.
존재하지 않거나 삭제된 게시글은 404, 잘못된 입력은 기존 ProblemDetail 형식의 400, 권한 부족은 403, 익명은 401이다.

기존 Tomcat의 큰 요청 처리에는 제한이 있다. 브라우저가 한도 초과 본문을 이미 전송하면 413 응답 대신 연결 오류로 보일 수 있다. #7 화면에서는 선택 파일 합계가 요청 한도를 넘지 않도록 먼저 안내한다.

## 등록·수정 요청

`multipart/form-data`의 `post` 파트는 Content-Type `application/json`으로 전송한다. `files`는 새로 추가할 파일마다 반복하며 생략 가능하다.

```json
{"title":"표준 공급 계약서","description":"작성 방법 안내","category":"CONTRACT","folderId":1,"removeFileIds":[]}
```

수정에서도 제목·분류·설명은 전체 값을 전달한다. 기존 첨부는 기본적으로 유지된다. `removeFileIds`에 삭제할 파일 ID를 넣고 새 `files`를 함께 보내면 원자적으로 교체한다. 다른 게시글 파일 ID·중복 ID는 거절한다. 등록 시 `removeFileIds`는 비어 있어야 한다.

등록 시 `folderId` 생략은 최상위 폴더다. 수정 시 생략하면 기존 소속을 유지하며, 다른 폴더 ID로 이동하려는 요청은 400이다. 기존 클라이언트의 요청을 그대로 지원한다.

```javascript
const data = new FormData();
data.append('post', new Blob([JSON.stringify({
  title: '표준 공급 계약서', category: 'CONTRACT', description: '개정본', removeFileIds: [12]
})], {type: 'application/json'}));
for (const file of selectedFiles) data.append('files', file);
await api.put('/forms/3', data); // Content-Type 경계값은 브라우저가 설정
```

상세 응답 예시:

```json
{
  "id": 3, "title": "표준 공급 계약서", "description": "개정본", "category": "CONTRACT", "folderId": 1,
  "createdAt": "2026-11-03T14:00:00", "createdBy": "manager1",
  "updatedAt": "2026-11-03T14:10:00", "updatedBy": "manager1",
  "files": [{"id": 13, "originalName": "계약서.pdf", "contentType": "application/pdf", "size": 1024,
             "createdAt": "2026-11-03T14:10:00", "createdBy": "manager1"}]
}
```

목록의 각 행은 위 상세에서 `files`를 제외한 게시글 정보다. 다운로드 파일명·바이트는 기존 파일 API를 따른다.

## DB 및 적용 순서

```mermaid
erDiagram
    form_folder ||--o{ form_folder : children
    form_folder ||--o{ form_post : forms
    form_post ||--o{ form_post_file : attachments
    stored_file ||--o| form_post_file : file
```

`V8__forms.sql`이 `form_post`, `form_post_file`을 만든다. 파일 ID UNIQUE로 양식 게시글 간 파일 공유를 막는다. 수정·삭제는 게시글 행 잠금으로 순차 처리하며 첨부 삭제 이력도 같은 트랜잭션에 기록한다.

`V9__form_folders.sql`은 `form_folder`와 `form_post.folder_id`를 추가한다. 기존 게시글은 첨부를 유지한 채 모두 최상위 폴더에 연결된다. 폴더명 UNIQUE 인덱스는 살아 있는 형제 폴더에 적용한다. 폴더 생성·이름 변경·삭제와 양식 생성은 부모 또는 대상 폴더 행을 잠가 중복명과 삭제된 폴더로의 추가를 막는다. 부모 ID만 먼저 조회하고 폴더 엔티티는 잠금 후 읽어, 대기 중 변경된 이름을 덮어쓰는 문제를 방지한다.

배포 순서: **#29의 V6 → #30의 V7 → #6의 V8**. 이 브랜치의 독립 테스트는 새 Testcontainers DB를 사용한다. V8을 먼저 적용한 개발 DB에 나중에 V6·V7을 추가하지 말고, 통합 검증에는 별도 DB를 사용한다. 기존 마이그레이션 수정·운영 DB 초기화·out-of-order 허용으로 우회하지 않는다.

검증 명령: `cd backend && ./gradlew test --tests '*FormApiTest'`.

검증 결과: `FormApiTest` 10개 + 실제 서버 `FormHttpTest` 2개를 포함해 백엔드 전체 80개 통과. HTTP 용량 제한 테스트는 1MB로 줄인 요청 한도를 사용한다.

#29·#30 머지 후 develop `961822c` 통합 검증: V1~V8 적용 및 전체 92개 테스트 통과, 프론트엔드 빌드 통과.

#7 검증: `./gradlew test --tests '*FormFolderApiTest' --tests '*FormApiTest'` — 폴더 9개와 기존 양식 10개 통과. 중복 생성, 동시 이름 변경, 양식 생성/폴더 삭제 양쪽 순서를 실제 PostgreSQL 잠금으로 확인한다. V9는 V8 이후 적용한다.

#7 전체 회귀 검증: 백엔드 101개 통과, 실패·오류·건너뜀 0개.
