# 기존 DB 업그레이드 CI 검증

빈 DB에서 성공하는 마이그레이션도 이미 높은 버전을 적용한 DB에서는 실패할 수 있다. 예를 들어 V9 없이 V10·V11을 먼저 적용한 뒤 V9를 추가하면 적용 순서 검증이 실패한다.

## 실행 방식

기존 `backend` 체크가 일반 `test`와 별도의 `migrationUpgradeTest`를 실행한다. 새 필수 체크 이름이나 외부 서비스는 추가하지 않는다.

| 실행 시점 | 기존 DB의 기준 |
|---|---|
| develop/main 대상 PR | 해당 이벤트의 대상 브랜치 SHA (`pull_request.base.sha`) |
| develop/main 푸시 | 푸시 직전 SHA (`before`) |

CI는 Git 이력에서 기준 마이그레이션 폴더를 추출한다. Testcontainers의 일회용 PostgreSQL 16에 기준 SQL을 적용하고 **가상 업체 1건**을 넣은 뒤, 현재 체크아웃의 SQL로 업그레이드한다. PR 체크아웃은 GitHub가 준비한 병합 결과를 사용한다. SQL 검증과 업체명·상태·비고 보존이 모두 통과해야 성공한다.

다음 경우에는 실패한다.

- 적용한 버전보다 낮은 마이그레이션이 뒤늦게 추가됨
- 적용한 SQL의 체크섬이 달라지거나 파일이 삭제됨
- 새 SQL 실행 오류, 잘못된 마이그레이션 파일명 또는 가상 업체 기록 손실
- 기준 커밋·폴더가 없거나 버전 SQL이 없음 (검증을 생략하지 않음)

최고 버전 파일 삭제도 검출하도록 **이 테스트에서만** Flyway의 기본 `*:future` 무시 규칙을 비운다. 순서 역전 허용(`outOfOrder`)은 끈다. 서비스의 Flyway 설정·마이그레이션 파일·DB 이력은 변경하지 않는다.

실제 운영 DB, 덤프, 기업 자료, 운영 접속 정보는 사용하지 않는다. 실패 시 기존 테스트 보고서와 업그레이드 보고서가 `backend-test-report` 아티팩트에 함께 올라간다.

## 로컬에서 실행하기

Docker가 실행 중이어야 한다. 저장소 루트에서 기준 브랜치의 파일을 임시 폴더로 추출한다. 필요하면 먼저 `git fetch origin`으로 기준 브랜치를 갱신한다.

```bash
baseline_root=$(mktemp -d)
git archive origin/develop backend/src/main/resources/db/migration | tar -x -C "$baseline_root"
cd backend
./gradlew migrationUpgradeTest \
  -PflywayBaselineDir="$baseline_root/backend/src/main/resources/db/migration"
```

`migrationUpgradeTest`는 실제 기준 DB 업그레이드 1건과 순서 역전·체크섬 변경·최고 버전 파일 삭제의 회귀 사례 3건을 실행한다. `./gradlew test`만 실행하면 기준 폴더가 필요한 이 클래스는 제외한다. 테스트 입력에 기준 폴더 내용도 등록하므로 기준 SQL이 달라지면 다시 검증한다.

## 병합·배포 전 확인

- 대상 브랜치가 바뀌면 최신 내용을 반영해 CI를 다시 통과시킨다. 오래된 PR 통과 결과가 이후의 병합 순서를 보장하지는 않는다.
- V9 → V10 → V11처럼 필요한 마이그레이션을 모두 포함한 코드로 배포한다. 번호 사이에 빈칸이 있다는 이유만으로 CI가 실패하지는 않는다. 이미 높은 버전이 적용된 뒤 낮은 버전을 추가하는 상황을 차단한다.
- 이미 적용한 SQL을 수정하거나 번호를 바꾸거나 이력을 지워 통과시키지 않는다. 후속 마이그레이션으로 수정한다.
- 이 검증은 Git 이력의 스키마와 대표 가상 기록을 확인한다. 실제 운영 DB의 수동 변경, 모든 업무 데이터, 백업·복구까지 보장하지 않는다.
