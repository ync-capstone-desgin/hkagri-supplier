# 협력업체 담당자·소통 메모 (#49)

업체마다 연락하는 사람이 여러 명이고, 통화·메일로 주고받은 내용이 남지 않아 담당자가 바뀌면 이어받기 어렵다. 업체 상세에 **담당자** 탭과 **메모** 탭을 둔다.

## 담당자 탭

- 기본정보의 **업체 담당자**(대표 담당자)는 그대로 두고, 그 밖에 연락하는 분을 여러 명 등록한다. 목록·엑셀의 업체 담당자 열은 바뀌지 않는다.
- 항목: 이름(필수), 직책, 전화번호, 이메일.
- 관리자·담당자가 추가·수정·삭제한다. 조회자는 보기만 한다.
- 추가·수정·삭제는 업체의 [변경 이력](change-log.md)에 남는다. 삭제는 지운 표시만 남긴다.

## 메모 탭

- **소통한 날짜**(기본값 오늘, 오늘 이후는 선택 불가)와 내용을 적어 남긴다. 어제 통화한 내용을 오늘 적어도 날짜가 맞게 남는다.
- 소통한 날짜가 최근인 순으로 보인다. 작성자와 작성 시각이 함께 남는다.
- **수정은 없다.** 잘못 쓴 메모는 작성자 본인이나 관리자가 지우고 다시 쓴다. 다른 담당자의 메모에는 삭제 버튼이 보이지 않는다.
- 관리자·담당자가 남긴다. 조회자는 보기만 한다.

## API

| 요청 | 권한 | 응답 |
|---|---|---|
| `GET /api/suppliers/{supplierId}/contacts` | 로그인 사용자 | 담당자 목록 (등록 순) |
| `POST /api/suppliers/{supplierId}/contacts` | 관리자·담당자 | 201. `{name, title, phone, email}` |
| `PUT /api/suppliers/{supplierId}/contacts/{contactId}` | 관리자·담당자 | 200 |
| `DELETE /api/suppliers/{supplierId}/contacts/{contactId}` | 관리자·담당자 | 204 |
| `GET /api/suppliers/{supplierId}/notes?page=0&size=20` | 로그인 사용자 | `{content, page, size, totalElements}` |
| `POST /api/suppliers/{supplierId}/notes` | 관리자·담당자 | 201. `{contactDate, content}` |
| `DELETE /api/suppliers/{supplierId}/notes/{noteId}` | 작성자 본인·관리자 | 204. 그 밖의 사용자는 403 |

메모 한 건의 `deletable`은 지금 로그인한 사람이 지울 수 있는지다. 다른 업체의 담당자·메모 ID를 넣으면 404, 없는 업체도 404다.

## DB

`V15__supplier_contacts_notes.sql`: `supplier_contact`, `supplier_note`. 둘 다 `deleted_at`, `deleted_by`로 지운 표시를 남긴다.

## 검증

`cd backend && ./gradlew test --tests '*SupplierContactApiTest'`
