# 테스트 문서 안내

## 1. 구성

기능별 시나리오와 실행 결과를 나눠 관리합니다.

| 기능 | 시나리오 | 유형·개수 |
| --- | --- | --- |
| 인증 | [인증 테스트](scenarios/AUTHENTICATION.md) | 단위 테스트 18개 |
| 프로젝트 검색 | [검색 테스트](scenarios/PROJECT_SEARCH.md) | 통합 테스트 21개 |
| 프로젝트 참여 흐름 | [사용자 흐름 E2E](scenarios/E2E_USER_FLOW.md) | 수동 시나리오·미실행 |

기준은 게시 코드 `b57d461`입니다. 로컬 미완료 수정은 제외했고, 아직 테스트를 실행하지 않았습니다. Cucumber 인수 테스트는 기존 코드가 있으나 이 형식으로는 정리하지 않았습니다.

## 2. 사용 방법

단위·통합 문서는 자동 테스트의 절차이며 아래 3~7절로 실행합니다. E2E 문서는 사람이 화면에서 직접 진행하는 시나리오이므로 해당 문서의 준비·진행 순서를 따릅니다. 자동화 구현은 포함하지 않습니다. 결과는 [양식](results/TEMPLATE.md)을 복사해 회차별로 남깁니다.

## 3. 공통 준비

- 개발자가 Java 17, Git, Gradle Wrapper와 의존성 다운로드 환경을 준비합니다.
- 현재 작업 폴더에는 미완료 코드가 있으므로 별도 게시 버전 작업 사본을 사용합니다.
- 운영·공유 DB나 실제 AWS·포털 자격증명이 유입되지 않는 전용 환경이어야 합니다. 셸·IDE·CI의 환경변수와 추가 프로필·설정을 확인하되 비밀값을 출력하지 않습니다.
- 인증은 기능 문서 2절의 대역과 설정 조건을 확인합니다. 설정 누락 사례에 외부 테스트 계정 설정을 주입하지 않습니다.
- 검색은 기능 문서 3절의 H2·전용 Redis·워밍업·S3 격리를 확인합니다. Redis·S3가 자동 준비·대체된다고 가정하지 않습니다.
- 전체 `test`를 무조건 실행하면 통합·외부 포털 테스트가 섞일 수 있습니다. 아래 명령처럼 대상을 선택합니다.

## 4. 게시 버전 작업 사본 준비

아래는 macOS/Linux 실행 예시이며, 이번 문서 작업에서 실행한 기록은 아닙니다.

```bash
# 원래 작업 폴더의 상태를 먼저 확인합니다.
git rev-parse --show-toplevel
git status --short

# 기존 미커밋 수정을 지우지 않고 별도 작업 사본을 만듭니다.
BASELINE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/meeteam-tests-b57d461.XXXXXX")"
git worktree add --detach "$BASELINE_DIR" b57d4611061054a2df87dfed3871ee94dbcb47de
cd "$BASELINE_DIR"
git rev-parse HEAD
git status --short
java -version
```

HEAD와 작업 상태를 확인합니다. 이 문서는 원래 폴더에서 함께 보며, 기존 미커밋 작업을 초기화하거나 삭제하지 않습니다.

## 5. 기능별 선택 실행

### 인증: 현재 단위 테스트 18개

인증의 준비 조건을 확인한 후 **별도 작업 사본**에서 실행하는 예시입니다.

```bash
bash ./gradlew test \
  --tests 'com.wardk.meeteam_backend.global.auth.service.TestAccountServiceTest' \
  --tests 'com.wardk.meeteam_backend.global.auth.service.TestAccountSejongLoginTest' \
  --rerun-tasks --console=plain
```

### 프로젝트 검색: 현재 통합 테스트 21개

검색 문서 3절의 추가 안전 조건과 전용 Redis 준비를 마친 뒤 **별도 작업 사본**에서 실행하는 예시입니다.

```bash
APP_WARMUP_ENABLED=false TEST_ACCOUNT_ENABLED=false bash ./gradlew integrationTest \
  --tests 'com.wardk.meeteam_backend.integration.project.service.ProjectQueryServiceIntegrationTest*' \
  --rerun-tasks --console=plain
```

끝의 `*`는 중첩 클래스도 선택하기 위한 값입니다. `--rerun-tasks`는 이전 결과 재사용을 피하는 옵션이며 성공을 보장하지 않습니다. 컴파일·의존성 문제로 막히면 그대로 기록하고 미완료 코드를 섞지 않습니다.

## 6. 보고서 확인과 결과 작성

아래 보고서 경로는 단위·통합 자동 테스트용입니다. 수동 E2E는 수행 단계·실제 결과·화면 증거를 기록합니다.

| 기능 | HTML 보고서 | XML 근거 |
| --- | --- | --- |
| 인증 | `build/reports/tests/test/index.html` | `build/test-results/test/TEST-*.xml` |
| 검색 | `build/reports/tests/integrationTest/index.html` | `build/test-results/integrationTest/TEST-*.xml` |

보고서의 클래스·메서드를 UT/IT 번호와 대조하고, 발견·실행·통과·실패·건너뜀 수를 적습니다. `BUILD SUCCESSFUL`만으로 전체 통과라고 판단하지 않습니다. 캐시·건너뜀·발견된 검사 없음도 새 실행 성공과 구분합니다.

전체 실행은 인증 18개 또는 검색 21개가 모두 수행됐는지 확인합니다. 일부만 실행했다면 대상과 제외 범위를 적습니다. [결과 양식](results/TEMPLATE.md)을 복사해 `results/YYYY-MM-DD_기능_회차.md`에 남깁니다.

판정은 **미실행 / 통과 / 실패 / 진행 불가 / 건너뜀 / 대상 제외**로 구분합니다. 실행 전 환경·컴파일 문제는 진행 불가, 검사 조건 불일치·검사 중 오류는 실패로 기록합니다. 비밀번호·인증 코드·토큰·쿠키·AWS 키는 증거에 넣지 않습니다.

## 7. 안전하게 마무리

- 보고서와 가려진 오류 기록을 먼저 보관합니다. 임시 폴더를 삭제한 뒤 증거가 남지 않을 수 있습니다.
- 직접 준비한 전용 Redis만 소유권·이름·포트를 확인하고 중지합니다. 공유 Redis를 중지·초기화하거나 `FLUSHALL`·`FLUSHDB`를 실행하지 않습니다.
- H2는 테스트 JVM의 메모리 DB입니다. `DB_CLOSE_DELAY=-1`은 같은 JVM 안에서 연결 종료 후에도 DB가 남을 수 있다는 뜻입니다. 시드 DELETE를 운영·공유 DB에 수동 실행하지 않습니다.
- 증거·변경을 확인하고 원래 저장소로 돌아온 뒤 개발자가 `git worktree remove "$BASELINE_DIR"`로 정리할 수 있습니다. 강제 삭제하지 않고 기존 미커밋 작업은 그대로 둡니다.
- 테스트 수정·재검증·커밋은 별도 작업입니다. 문서 정리만으로 수행했다고 기록하지 않습니다.

## 8. 작성 기준

- [전체 테스트 컨벤션](../TEST_CONVENTION.md)

Cucumber 시나리오와 실행 코드의 연결, 현재 API와의 불일치 및 실행 전 주의사항은 위 문서를 참고합니다. 작성 기준과 시나리오는 실제 실행 결과와 구분합니다.
