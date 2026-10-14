# main 자동 배포: GitHub Actions 하나로 구성

대상은 현재 서울 리전의 기존 `meeteam-test` EC2 테스트 서버입니다. CodePipeline, ECR, S3, AWS 액세스 키, SSH 키 공유가 필요 없습니다.

```text
main push (또는 main에서 수동 실행)
  → CI: 안내 출력만 수행, 테스트/린트 없음
  → GitHub hosted runner: Java 17 bootJar 빌드, 테스트 생략
  → 같은 실행의 JAR artifact 다운로드
  → 기존 EC2의 repository-scoped runner: Docker 앱만 교체
  → health + 99990001 로그인/인증 프로필 검사
  → 실패 시 이전 앱 이미지 복구, 워크플로는 실패로 표시
```

CI 단계는 요청대로 코드 검사를 하지 않습니다. **컴파일/배포 실패까지 숨겨서 녹색으로 만들지는 않습니다.** 컴파일에 실패하면 기존 앱을 교체하지 않습니다. 별도 AWS 리소스를 만들지 않지만 기존 EC2, 공인 IP 등의 비용은 유지됩니다. GitHub Actions 비용/사용 한도는 계정 정책에 따릅니다.

## 처음 한 번만 해야 하는 설정

### 1. 이 변경을 GitHub main에 반영

- `.github/workflows/deploy-main.yml`
- `scripts/deploy-main.sh`, `scripts/setup-test-runner.sh`
- `AuthService.java`의 숫자 테스트 로그인 분기
- `TestAccountService.java`, `TestAccountController.java` 및 관련 테스트
- 이 문서와 비밀 파일 제외 `.gitignore` 변경

테스트 로그인 코드를 빼고 main 코드만 배포하면 검증에 실패해 이전 앱으로 복구됩니다.

**올리면 안 되는 것:** 서버의 `.env`, `.env.test-account`, DB SQL 백업, runner `.credentials`/`.credentials_rsaparams`, 인증 쿠키. 이번 artifact는 JAR/Dockerfile/보안 설정/배포 스크립트만 포함합니다.

### 2. GitHub에서 repository runner 등록 토큰 발급

저장소의 **Settings → Actions → Runners → New self-hosted runner → Linux / x64**에서 등록용 토큰을 확인합니다. 저장소 관리 권한이 필요합니다.

- 토큰은 짧은 시간만 유효한 등록용 토큰입니다. Personal Access Token/비밀번호가 아닙니다.
- **채팅이나 코드에 토큰을 붙여넣지 마세요.** 아래 서버 설치 스크립트의 숨김 입력창에만 입력합니다.
- 반드시 이 **저장소 전용 runner**로 등록합니다. org 공용 runner를 만들지 않습니다.

### 3. Ubuntu EC2 터미널에서 설치

이 스크립트가 main에 올라간 뒤 다음을 실행합니다.

```bash
curl -fsSL https://raw.githubusercontent.com/sejong-meeteam/meeteam-backend/main/scripts/setup-test-runner.sh -o /tmp/setup-meeteam-runner.sh
bash /tmp/setup-meeteam-runner.sh
```

공식 GitHub runner 배포 파일의 SHA-256을 확인하고 설치합니다. 숨김 입력창에서 등록 토큰을 입력합니다. `ubuntu` 사용자의 systemd 서비스로 설치하고, 라벨 `meeteam-test`를 붙입니다.

GitHub Runners 화면에 이 runner가 **Idle/Online**이면 준비된 것입니다. Linux/X64 기본 라벨과 `meeteam-test` 라벨이 모두 필요합니다.

## 공개 저장소 runner 보안 제한

이 저장소는 공개 저장소입니다. self-hosted runner는 Docker 권한으로 서버 파일·비밀 설정에 접근할 수 있으므로 **신뢰하는 main 코드만 실행해야 합니다.**

- 이 워크플로는 `push: main`/`workflow_dispatch`만 사용하고 repository/ref도 검사합니다.
- **pull_request, pull_request_target, 외부 fork 코드**를 이 runner에서 실행하지 마세요.
- main 쓰기 권한을 신뢰하는 담당자로 제한하고, 가능하면 branch protection/PR 승인을 사용하세요.
- 새 워크플로를 추가할 때도 이 runner 라벨을 무심코 사용하지 마세요.
- 외부 PR을 자동 실행해야 한다면 이 방식 대신 GitHub-hosted runner + AWS OIDC/SSM 등 별도 격리 배포로 전환하세요.

## 첫 실행 확인

저장소 **Actions → Main - minimal CI and test EC2 deploy → Run workflow → main**을 선택합니다.

1. `ci`: 성공 (검사 없음)
2. `build`: JAR 컴파일 성공
3. `deploy`: EC2 runner에서 실행
4. 로그의 `DEPLOY SUCCESS: <commit SHA>` 확인
5. `https://meeteam.fignal.me/actuator/health`의 `UP` 확인
6. 기존 웹 화면에서 학번 `99990001`과 기존 테스트 비밀번호로 로그인 확인

이후 main push마다 같은 과정이 자동 실행됩니다. Docker 앱만 잠깐 교체되므로 완전한 무중단 배포는 아닙니다.

## 보존되는 서버 상태

- `/home/ubuntu/meeteam/.env`, `.env.test-account`: 기존 값 그대로 유지
- DB/Redis: 컨테이너와 볼륨 교체/삭제 안 함
- Nginx/HTTPS/Elastic IP/DNS: 변경 안 함
- DB: 교체 전에 비공개 SQL 백업, Hibernate `validate`, SQL init `never`, Flyway 비활성화
- 테스트 계정: 기존 DB 계정과 비밀번호를 그대로 사용
- 배포별 app override: `/home/ubuntu/meeteam/.deploy/`
- 마지막 성공 SHA: `/home/ubuntu/meeteam/.deploy/current-revision`

엔티티/DB 스키마 변경은 별도 안전한 마이그레이션이 필요합니다. 이 워크플로는 테이블을 자동 삭제·초기화하지 않습니다.

## 서버 중지/실패 시

- EC2가 꺼져 있으면 deploy가 runner를 기다립니다. 서버가 켜져 있어야 배포됩니다.
- 중지→시작 시 runner 서비스와 기존 앱은 자동 재시작됩니다.
- 대기가 너무 길어 워크플로가 만료되면 서버 시작 후 Actions에서 다시 실행하세요.
- health 또는 테스트 로그인 검사 실패 시 이전 앱 이미지를 다시 실행하고 건강 상태를 확인합니다. 실패는 Actions에 빨간색으로 표시합니다.
- 배포 실행 중 수동 취소·강제 종료는 피하세요. 정상 main push는 진행 중 배포를 취소하지 않습니다.
- 이전 이미지와 DB 백업은 복구용으로 유지하므로 `df -h`, `docker system df`로 디스크 사용량을 주기적으로 확인하세요. 무분별한 `docker system prune -a`, `down -v`는 하지 마세요.

기존 master/test-server 워크플로는 이 main 배포에 사용하지 않습니다. 같은 서버를 다른 워크플로/수동 명령으로 동시에 배포하지 마세요.
