# 자료 종류와 필수 서류 점검 (#54, #47)

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

## 필수 서류 점검 (#47)

업체마다 받아야 할 서류가 빠졌는지 확인한다. 별도 목록을 만들지 않고 **자료 종류에 '필수' 표시**를 둔다.

- **정하기**: 관리자가 자료 종류 관리에서 종류별 **필수 서류** 스위치를 켠다. 처음에는 아무 종류도 필수가 아니다. 사용 중지한 종류는 필수로 둘 수 없다.
- **업체 상세**: 자료 탭 맨 위에 필수 서류별로 `제출 N` 또는 `누락`이 보이고, 누락이 있으면 화면 상단에 **필수 서류 누락 N건**이 나온다.
- **협력업체 목록**: **서류 누락** 열에 빠진 종류 수가 보이고, **필수 서류 누락만**을 체크하면 누락 업체만 모아 본다. 엑셀에도 열이 들어간다.

| 상황 | 판정 |
|---|---|
| 그 종류의 파일이 업체에 하나라도 있음 | 제출. 업체 일반 자료뿐 아니라 계약·인증에 붙인 파일도 센다 |
| 파일을 삭제하거나 종류를 바꿔 하나도 남지 않음 | 다시 누락 |
| 필수 표시를 끔 | 점검 대상에서 빠진다 |

필수 목록은 모든 업체에 공통으로 적용하고, 업체마다 다른 부분은 **해당 없음**으로 뺀다 (#85).

- 업체 상세 자료 탭의 필수 서류 표시에서 누락된 서류를 눌러 **해당 없음**으로 뺀다. 관리자와 담당자가 할 수 있고, 누가 언제 뺐는지 `doc_exemption`에 남는다.
- 뺀 서류는 회색 `해당 없음`으로 보이고 그 업체의 누락 건수와 **필수 서류 누락만** 목록에서 빠진다. 다시 누르면 되돌린다. 다른 업체에는 영향이 없다.
- API: `PUT /api/suppliers/{id}/required-documents/{categoryId}/exempt` 본문 `{"exempt": true|false}`. 점검 결과의 각 줄에 `exempt`가 들어간다.
- `V19__doc_exemption.sql`이 `doc_exemption(supplier_id, category_id, created_at, created_by)`을 추가한다.

업체 유형별로 필수 목록 자체를 다르게 두는 기능은 없다. 파일이 있는지만 보고, 서류의 유효기간이나 내용은 확인하지 않는다.

## API

| 요청 | 권한 | 기능 |
|---|---|---|
| `GET /api/file-categories` | 로그인 사용자 | 전체 종류 (`id`, `name`, `active`), 표시 순서대로 |
| `POST /api/admin/file-categories` `{name}` | 관리자 | 종류 추가 (201) |
| `PUT /api/admin/file-categories/{id}` `{name}` | 관리자 | 이름 수정 |
| `PATCH /api/admin/file-categories/{id}/active` `{active}` | 관리자 | 사용 중지·다시 사용 |
| `PATCH /api/admin/file-categories/{id}/required` `{required}` | 관리자 | 필수 서류로 정하기·해제 |
| `GET /api/suppliers/{supplierId}/required-documents` | 로그인 사용자 | 필수 서류별 `{categoryId, name, fileCount, submitted}` |
| `GET /api/suppliers?missingDocs=true` | 로그인 사용자 | 필수 서류가 빠진 업체만. 목록·상세 응답에 `missingDocCount` 추가 |
| `PATCH /api/files/{id}/category` `{categoryId}` | 관리자·담당자 | 파일의 종류 변경. `null`이면 지움 |
| `POST /api/suppliers/{supplierId}/files` | 관리자·담당자 | 기존 업로드에 `categoryId`(선택) 추가 |
| `GET /api/suppliers/{supplierId}/files?categoryId=` | 로그인 사용자 | 종류로 걸러 보기. 응답에 `categoryId`, `categoryName` 추가 |
| `GET /api/files/search?categoryId=` | 로그인 사용자 | 종류로 걸러 보기. 응답에 `categoryName` 추가 |

없는 종류나 사용 중지한 종류를 붙이려 하면 400(`categoryId`)이다.

## DB

`V14__file_category.sql`: `file_category` 테이블과 처음 종류 7개, `stored_file.category_id`(선택) 추가. `V16__required_documents.sql`: `file_category.required` 추가.

## 검증

`cd backend && ./gradlew test --tests '*FileCategoryApiTest' --tests '*RequiredDocumentApiTest'`
