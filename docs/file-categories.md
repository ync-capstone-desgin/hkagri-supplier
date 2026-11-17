# 자료 종류 (#54)

업체 자료가 **어떤 서류인지**(사업자등록증, 계약서, 성적서 등) 파일마다 표시한다. 파일 이름만으로는 구분하기 어려울 때 종류로 걸러 본다.

## 사용 방법

- **올릴 때**: 업체 상세 → 자료 탭의 **자료 종류 (선택)** 에서 고른 뒤 파일을 올린다. 고르지 않아도 올라간다.
- **올린 뒤**: 자료 목록의 **자료 종류** 칸에서 바로 바꾸거나 지운다 (관리자·담당자).
- **걸러 보기**: 자료 탭 목록 위의 선택상자, 또는 **자료 검색** 화면의 자료 종류 조건을 쓴다.
- **종류 관리**: 관리자는 자료 검색 화면의 **자료 종류 관리**에서 종류를 추가하고, 이름을 고치고, 사용 중지한다.

업체 일반 자료와 계약·인증에 붙인 파일 모두에 종류를 붙일 수 있다. 양식 자료실은 이미 분류와 폴더가 있어 대상이 아니다.

## 규칙

| 상황 | 동작 |
|---|---|
| 종류를 고르지 않고 올림 | 종류 없이 올라간다 |
| 같은 이름의 종류를 추가 | 공백·대소문자만 달라도 막는다. 사용 중지한 종류도 중복으로 본다 |
| 종류를 사용 중지 | 새로 고를 수 없다. 이미 붙인 파일에는 그대로 보인다 |
| 종류 이름을 수정 | 이미 붙인 파일에도 새 이름이 보인다 |
| 종류 삭제 | 없다. 이미 붙인 파일을 보존하려고 사용 중지만 둔다 |

처음에는 사업자등록증, 통장 사본, 계약서, 인증서, 표기사항, 품목제조보고서, 성적서 7개가 들어 있다. 일반적인 서류 이름으로 넣어 둔 것이며 **한국농산이 확정한 목록은 아니다.** 답변을 받으면 관리 화면에서 고친다.

## API

| 요청 | 권한 | 기능 |
|---|---|---|
| `GET /api/file-categories` | 로그인 사용자 | 전체 종류 (`id`, `name`, `active`), 표시 순서대로 |
| `POST /api/admin/file-categories` `{name}` | 관리자 | 종류 추가 (201) |
| `PUT /api/admin/file-categories/{id}` `{name}` | 관리자 | 이름 수정 |
| `PATCH /api/admin/file-categories/{id}/active` `{active}` | 관리자 | 사용 중지·다시 사용 |
| `PATCH /api/files/{id}/category` `{categoryId}` | 관리자·담당자 | 파일의 종류 변경. `null`이면 지움 |
| `POST /api/suppliers/{supplierId}/files` | 관리자·담당자 | 기존 업로드에 `categoryId`(선택) 추가 |
| `GET /api/suppliers/{supplierId}/files?categoryId=` | 로그인 사용자 | 종류로 걸러 보기. 응답에 `categoryId`, `categoryName` 추가 |
| `GET /api/files/search?categoryId=` | 로그인 사용자 | 종류로 걸러 보기. 응답에 `categoryName` 추가 |

없는 종류나 사용 중지한 종류를 붙이려 하면 400(`categoryId`)이다.

## DB

`V14__file_category.sql`: `file_category` 테이블과 처음 종류 7개, `stored_file.category_id`(선택) 추가.

## 검증

`cd backend && ./gradlew test --tests '*FileCategoryApiTest'`
