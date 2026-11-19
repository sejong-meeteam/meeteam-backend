# 1차 UT 개선 배포 런북 (V9·V10)

운영 서버(`main` → EC2)는 `ddl-auto=validate`이고 Flyway가 꺼져 있습니다(`scripts/deploy-main.sh`). 그래서 새 컬럼과 테이블을 DB에 먼저 적용하지 않고 `main`을 배포하면 새 앱이 스키마 검증에 실패하고, 배포 스크립트가 이전 버전으로 롤백합니다. V7·V8과 같은 순서로 진행합니다.

- 브랜치: `feat/ut-round1`
- 마이그레이션:
  - `src/main/resources/db/migration/V9__ut_round1_project_job_application.sql`: 협업 방식 컬럼, 카테고리·직군 코드 VARCHAR 전환, 마케팅·데이터 직군과 기술 스택, `decided_at`
  - `src/main/resources/db/migration/V10__project_proposal.sql`: `project_proposal` 테이블, `notification.proposal_id`, `notification.type` VARCHAR 전환
- 두 파일 모두 추가와 타입 확장만 합니다. 기존 행은 지우지 않습니다. 실제 MySQL 8 컨테이너에서 기존 스키마에 적용한 뒤 `ddl-auto=validate` 기동까지 확인했습니다.

## 1. 서버에서 백업과 적용

EC2(`/home/ubuntu/meeteam`)에서 실행합니다. 이 저장소의 두 SQL 파일을 서버의 `~/meeteam/migrations/`에 먼저 복사해 둡니다.

```bash
cd /home/ubuntu/meeteam
stamp=$(date +%Y%m%d-%H%M%S)

# 1) 백업 (반드시 성공해야 다음 단계로)
docker exec meeteam-test-mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces "$MYSQL_DATABASE"' \
  > "backups/manual-before-v9-v10-$stamp.sql"
test -s "backups/manual-before-v9-v10-$stamp.sql" && echo BACKUP_OK

# 2) V9 → V10 순서로 적용
for f in migrations/V9__ut_round1_project_job_application.sql migrations/V10__project_proposal.sql; do
  docker exec -i meeteam-test-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < "$f" \
    && echo "APPLIED $f" || { echo "FAILED $f"; break; }
done
```

## 2. 적용 결과 확인

```bash
q() { docker exec meeteam-test-mysql sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot \"\$MYSQL_DATABASE\" -e \"$1\""; }
q "SHOW COLUMNS FROM project LIKE 'meeting%'; SHOW COLUMNS FROM project LIKE 'weekly_hours';"
q "SHOW COLUMNS FROM project_member_application LIKE 'decided_at';"
q "SHOW TABLES LIKE 'project_proposal'; SHOW COLUMNS FROM notification LIKE 'type';"
q "SELECT job_field_id, code FROM job_field ORDER BY job_field_id;"
```

- `job_field`에서 `MARKETING`과 `DATA`의 ID가 **7과 8**이어야 합니다.
- 프론트 팀원 필터는 직군 ID를 코드에 고정해 두고 씁니다(`components/features/team/useTeammateFinder.ts`의 `JOB_FIELD_ID_BY_CODE`). 다른 값이면 그 표를 실제 ID로 고쳐야 합니다.

## 3. 백엔드 배포

```bash
git switch main && git merge --ff-only feat/ut-round1 && git push origin main
```

GitHub Actions의 "Main - minimal CI and test EC2 deploy"가 끝나면 아래를 확인합니다.

- `https://meeteam.fignal.me/actuator/health`가 200
- `GET /api/v1/jobs/options`에 MARKETING·DATA가 있음
- 프론트(0.4.0, 이미 배포됨)에서 다음이 동작함
  - 공개 프로필의 "제안 보내기"로 프로젝트 목록이 열림
  - 지원자 관리의 승인됨·거절됨 탭
  - "온라인 가능" 필터
  - 스터디·공모전 등록
  - 협업 방식 저장

## 4. 되돌리기

- **앱:** 배포 스크립트가 기동 실패 시 이전 이미지로 자동 롤백합니다.
- **DB:** V9·V10은 추가만 하므로 이전 앱도 그대로 동작합니다. 꼭 되돌려야 하면 1단계의 백업 파일로 복원합니다.
