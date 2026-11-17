# 변경 이력 (#45)

협력업체·계약·인증을 **누가 언제 무엇을 바꿨는지** 남긴다. 등록·수정·갱신·처리상태 변경·삭제가 대상이다.

## 보는 곳

| 화면 | 내용 | 볼 수 있는 사람 |
|---|---|---|
| 업체 상세 → **변경 이력** 탭 | 그 업체와 그 업체의 계약·인증 이력을 최신순으로. 삭제된 계약·인증의 이력도 남는다 | 로그인한 모든 사용자 |
| 메뉴 **변경 이력** | 전체 이력. 대상 종류와 검색어(업체명·대상 이름·작업자)로 찾는다. 삭제된 업체의 이력도 여기서 찾는다 | 관리자 |

## 남는 내용

| 작업 | 변경 내용 칸 |
|---|---|
| 등록 | 입력한 항목과 값 |
| 수정 | **달라진 항목만** `이전 값 → 바뀐 값`. 달라진 것이 없으면 이력을 남기지 않는다 |
| 갱신 | 새로 등록한 건의 이력으로 남고, 갱신 전 건과 달라진 항목을 보여 준다 |
| 처리상태 변경 | `갱신 처리상태: 미착수 → 갱신진행` |
| 삭제 | 항목 없이 삭제했다는 사실만 |

업체명·대상 이름·작업자 이름은 기록 시점의 값을 그대로 저장한다. 나중에 이름이 바뀌거나 대상이 삭제돼도 이력은 그대로 읽을 수 있다.

이 기능을 넣기 전의 변경과, 시연용 샘플 데이터는 이력이 없다. 첨부 파일 올리기·삭제는 이력 대상이 아니다 (파일 삭제 이력은 기존 관리자 기능에 있다).

## API

| 요청 | 권한 | 응답 |
|---|---|---|
| `GET /api/suppliers/{supplierId}/changes?page=0&size=50` | 로그인 사용자 | `{content, page, size, totalElements}` |
| `GET /api/admin/changes?targetType=&keyword=&page=0&size=50` | 관리자 | 같음 |

`targetType`은 `SUPPLIER`·`CONTRACT`·`CERTIFICATION`. 한 줄의 형태:

```json
{"id": 12, "targetType": "CONTRACT", "targetLabel": "계약", "targetId": 3, "supplierId": 2, "supplierName": "가상-여름복숭아농원",
 "targetName": "복숭아 원료 공급 계약", "action": "UPDATE", "actionLabel": "수정",
 "changes": [{"label": "종료일", "before": "2026-11-15", "after": "2027-03-31"}],
 "actor": "manager1", "actorName": "김담당", "createdAt": "2026-11-17T10:41:00"}
```

## DB와 구현

- `V13__change_log.sql`의 `change_log` 테이블. 대상이 삭제돼도 이력이 남도록 외래키를 걸지 않았다. 바뀐 항목은 `changes` 열에 JSON 배열로 저장한다.
- 협력업체·계약·인증 서비스가 작업 전후의 값을 화면 항목 이름으로 뽑아 `ChangeLogService.record`에 넘기고, 서비스가 달라진 항목만 골라 저장한다. 같은 트랜잭션이라 작업이 실패하면 이력도 남지 않는다.

## 검증

`cd backend && ./gradlew test --tests '*ChangeLogApiTest'`
