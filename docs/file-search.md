# 자료 검색 (#53)

어느 업체에 올렸는지 몰라도 모든 자료를 한 번에 찾는다. 10/20 미팅에서 나온 "엑셀과 파일을 직접 뒤져서 찾는다"는 불편을 줄이기 위한 화면이다.

## 사용 방법

1. 왼쪽 메뉴 **자료 검색**을 연다. 검색어 없이 열면 최근에 올린 순으로 전체가 보인다.
2. 검색어를 넣고 Enter 또는 **조회**를 누른다.
3. 결과에서 **내려받기**로 바로 받거나, 업체명·양식 제목을 눌러 그 파일이 있는 업체 상세·양식 폴더로 이동한다.

로그인한 모든 사용자(조회자 포함)가 쓸 수 있다.

## 찾는 범위와 검색어

| 위치 | 대상 |
|---|---|
| 업체 자료 | 업체 상세의 자료 탭에 올린 일반 자료 |
| 계약 첨부 · 인증 첨부 | 계약·인증 건에 붙인 파일 (갱신되어 이전 이력이 된 건의 파일 포함) |
| 양식 자료실 | 양식 게시글의 첨부 |

검색어는 아래 중 하나에 **일부만 맞아도**(대소문자 무시) 찾는다. 파일 이름이 `scan001.pdf`처럼 단서가 없어도 계약명이나 업체명으로 찾을 수 있다.

- 파일 이름
- 업체명
- 계약명, 계약번호
- 인증 종류, 적용 대상, 인증번호
- 양식 제목

삭제한 파일, 삭제한 계약·인증·양식의 파일은 나오지 않는다. 파일 **내용**은 검색하지 않는다.

## API

`GET /api/files/search?keyword=&page=0&size=20` — 로그인 사용자. `{content, page, size, totalElements}`, 최신순.

```json
{"file": {"id": 31, "originalName": "scan001.pdf", "contentType": "application/pdf", "size": 12106,
          "createdAt": "2026-11-17T10:50:00", "createdBy": "manager1"},
 "location": "계약 첨부", "supplierId": 2, "supplierName": "가상-여름복숭아농원",
 "contractName": "DEMO-CT-002 · 복숭아 원료 공급 계약", "certificationName": null,
 "formId": null, "formTitle": null, "folderId": null}
```

내려받기는 기존 `GET /api/files/{id}/download`를 쓴다.

## 검증

`cd backend && ./gradlew test --tests '*FileSearchApiTest'`
