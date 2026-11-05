# GitHub 전체 연동 구현 및 적용 안내

## 상태

계정 연결만이 아니라 저장소 연결·개발 현황·웹훅·협업자 권한·알림까지 로컬 코드에 구현했습니다. 커밋·푸시·배포·실서버 SQL 적용은 하지 않았습니다. 실제 GitHub App 비밀값도 생성하거나 변경하지 않았습니다.

코드 구현, 격리 테스트, 실제 GitHub 인증, 실서버 적용은 별개의 상태입니다. 실제 인증·설치·초대·권한 회수를 확인했다는 뜻이 아닙니다.

## 구현 범위

| 기능 | 구현 내용 |
|---|---|
| 개인 계정 | 조회·OAuth 연결·연결 해제, 서명 state·1회용 nonce·PKCE·로그인 회원 바인딩 |
| App 설치 | 설치 주소, 설치 후 추가 OAuth 증명, 설치/회원/프로젝트 재검증 |
| 저장소 연결 | 연결 상태, 사용 가능한 저장소 목록, 연결·해제, 자동 초대·회수 설정 |
| 개발 현황 | 저장소 집계, 기본 브랜치 활동, 서울 월요일 기준 최근 8주 커밋, 언어, 기여자 |
| 카드 배지 | 홈·검색·프로필·레거시 목록의 githubActivity를 일괄 조회로 보강, 캐시된 카드에도 최신 배지 반영 |
| 팀 진행 | 열린 PR, GraphQL 리뷰 상태, 72시간 정체 판단, 최근 28일 팀원별 기여 |
| 주간 리포트 | 서울 월요일~일요일, 커밋·머지 PR·이슈·팀원 기여, 월요일/미래 입력 검증 |
| 웹훅 | 서명·크기 제한·중복 방지·영속 큐·원자적 처리 영수증, 지정된 이벤트 반영 |
| 협업자 | 전체 팀원 상태, 리더 수동 초대, 자동 초대/회수, 초대 취소, 실패·재시도 |
| 알림 | GITHUB_REPO_INVITED, GITHUB_REVIEW_REQUESTED, GITHUB_PR_MERGED, 기존 저장/SSE 흐름 재사용 |

조회는 DB에 저장한 자료를 반환합니다. 활동 조회 요청마다 GitHub API를 호출하지 않습니다. 연결 직후, 웹훅 처리 후, 기본 1시간 간격으로 동기화 작업을 예약합니다. 대기 중인 같은 저장소 동기화는 합치고, 실행 중인 작업 뒤의 후속 갱신은 유지합니다.

최초 동기화 전에는 연결된 저장소를 미연결이나 가짜 0 통계로 반환하지 않습니다. 개발 현황/진행/리포트는 한국어 재시도 오류를 반환하고, 카드 배지는 아직 표시하지 않습니다. 최초 이벤트 백필은 최근 8주이며 이후 이벤트는 누적됩니다. 수집 범위 이전의 완전한 과거 이력 전체를 가져오는 기능은 아닙니다. 지난주 자료를 별도 확정·마감하는 배치는 없으며 DB 이벤트를 기간 집계합니다.

기본 브랜치 push 웹훅은 실제 push 시각(`repository.pushed_at`, 없으면 수신 시각)을 사용합니다. 개별 커밋의 주간 집계는 각 커밋 시각으로 계산합니다. 초기 동기화의 기본 브랜치 최신 커밋 시각은 push 기록이 없을 때의 보조 자료이며, 이후 저장된 더 최신 push 시각을 덮어쓰지 않습니다.

## 보안과 개인정보

- 비공개 저장소의 비팀원 응답은 기여자를 숨기고, 저장소 ID는 0, 이름/주소/설명/브랜치는 빈 문자열로 내립니다. 집계값만 공개합니다.
- 숨김/삭제 프로젝트는 공개 API에서 404이며, 리더·팀원 권한은 각 보호 API에서 확인합니다.
- 동기화는 네트워크 조회 후 잠근 연결 행의 bindingVersion과 rowVersion을 다시 검증합니다. 오래된 응답이 새 비공개 상태나 변경된 연결을 되돌리지 않습니다.
- repository 웹훅은 실제 값이 같아도 버전을 명시적으로 증가시킵니다. 오래된 public 이벤트만으로 private 상태를 해제하지 않습니다.
- GitHub 호스트 고정, HTTP 리다이렉트 비활성, 요청 타임아웃, 페이지 제한, 응답 검증을 적용합니다. 페이지 제한 초과나 불완전한 응답을 조용히 완성된 집계로 취급하지 않습니다.
- GitHub App JWT는 메모리에서 RSA 서명하며, GitHub 다운로드 PKCS#1 PEM과 PKCS#8 PEM을 지원합니다. Installation token과 user access/refresh token은 DB·Redis에 저장하지 않습니다.
- 파일 본문/패치 API를 호출하지 않습니다. 웹훅 원문은 저장하지 않고 필요한 ID·시각·PR/이슈 제목 등 메타데이터만 추립니다. 본문, diff, 코드, 커밋 메시지, 이메일, 토큰은 영속 큐에 넣지 않습니다.
- OAuth·설치·웹훅·권한 작업의 인자/반환값은 자동 상세 로깅에서 제외합니다. GitHub API 예외·SQL·토큰을 클라이언트에 그대로 전달하지 않습니다.

## 설치 콜백은 추가 증명이 필요합니다

`installation_id`와 서명된 state만으로는 해당 설치에 대한 GitHub 권한을 증명할 수 없습니다. 따라서 설치 후 **추가 OAuth 한 번**으로 다음을 확인합니다.

1. 돌아온 브라우저가 인가를 시작한 MeeTeam 회원인지 확인.
2. 임시 GitHub user token의 숫자 ID가 현재 연결된 계정인지 확인.
3. `/user/installations/{id}/repositories`에서 실제 `permissions.admin=true`인 저장소만 허용 목록으로 저장.
4. App 설치 정보의 App ID·설치 ID·정지 상태 확인.
5. 회원 상태·계정 연결 버전·리더 권한·프로젝트 연결 버전을 잠금 아래 다시 검증한 뒤 저장.

다른 프로젝트에 이미 연결한 저장소는 목록에서 제외하며, 연결 시에도 유일성 제약으로 경합을 막습니다. 설치에 저장소를 추가했다고 곧바로 다른 저장소까지 프로젝트에 공개하지 않으며, 새 권한 증명을 거쳐 허용 목록을 갱신해야 합니다.

## 자동 권한 처리

기존 프로젝트·계정 서비스의 미완료 코드를 덮어쓰지 않도록 전용 생명주기 Aspect로 성공 지점에 연결했습니다. GitHub 기능 활성화 설정은 트랜잭션 어드바이스가 먼저 진입하도록 순서를 지정합니다. 실제 Spring/H2 테스트로 원본 트랜잭션 안에서 이벤트가 발행되고, 실패 시 원본 변경과 작업이 같이 롤백되는지 검증합니다.

- 합류/지원 승인, 계정 연결, 저장소 연결: 조건을 만족하는 팀원 초대 예약.
- 팀원 탈퇴·방출, 관리자 방출·강제 탈퇴, 회원 탈퇴: 숫자 GitHub ID를 캡처하여 회수 예약.
- 프로젝트 삭제: 연결·협업자 기록 정리. 이미 받은 GitHub 권한은 유지.
- 계정/저장소 연결 해제: 이미 받은 권한은 직접 회수하지 않음.
- 실제 GitHub 호출은 원본 트랜잭션 커밋 후 작업자에서 실행. GitHub 실패로 이미 승인된 가입/탈퇴를 되돌리지 않음.
- 리더의 수동 초대는 실제 201/204 결과를 받은 뒤 INVITED/ACTIVE를 반환하며 네트워크 호출 중 DB 트랜잭션을 잡지 않음.
- 자동 작업은 실행 시 설정과 멤버십을 다시 확인. 이미 INVITED/ACTIVE인 계정은 자동 재초대하지 않음.
- 바뀐 GitHub 계정의 옛 권한도 잃어버리지 않도록 숫자 ID·연결 세대별 이력을 보존. 탈퇴 후 계정 행이 삭제되어도 회수 가능.
- GitHub가 초대를 받았으나 DB 기록 전 프로세스가 죽은 경우에도 저장소 초대 목록을 숫자 invitee ID로 대조하여 취소.
- 리더나 현재 같은 저장소에 정당한 권한이 있는 팀원의 계정을 오래된 작업으로 삭제하지 않음.
- 연결 해제 중 완료된 새 초대는 보상 작업으로 정리. 원래부터 있던 권한을 무조건 회수하는 동작과 구분.
- 리더 ACTIVE는 단순 설치 표시가 아니라 설치 시 증명한 숫자 계정과 현재 계정이 같을 때만 기본 인정하며, 이후 웹훅의 명시적 상태가 우선.

## 영속 큐

`github_work`에 먼저 저장하고 작업자가 별도 짧은 트랜잭션으로 점유합니다. 네트워크 호출은 점유 트랜잭션 밖에서 실행합니다.

- 원자적 행 잠금, 15분 임대, 실행 중 60초 간격 갱신.
- 재점유 시 attempt를 증가시켜 이전 작업자의 완료/실패 처리가 새 임대를 덮어쓰지 않도록 함.
- 실패는 최대 5회 지수 지연 후 FAILED. 협업자 실패 상태와 한국어 원인을 저장하여 수동 재시도 가능.
- 웹훅 delivery 키는 중복 방지를 위해 유지하고, 완료한 작업 payload는 비움.
- 웹훅 영수증과 로컬 이벤트/알림은 같은 트랜잭션으로 기록하여 재실행 시 중복 부작용 방지.

## 실제 설정: 아직 적용하지 않음

### GitHub App 화면

등록된 GitHub App을 새로 만들거나 변경하기 전 기존 App/설치 상태를 확인합니다. 실제 프론트 호스트를 사용하여 다음을 등록합니다.

- User authorization callback URL 1: `https://{프론트}/api/v1/github/oauth/callback`
- User authorization callback URL 2: `https://{프론트}/api/v1/github/app/setup/oauth/callback`
- Setup URL: `https://{프론트}/api/v1/github/app/setup` (Redirect on update 체크)
- Webhook URL: `https://{실제-백엔드}/api/v1/github/webhooks`
- Request user authorization during installation: 끔.

브라우저 콜백은 같은 프론트 origin의 기존 Next 프록시를 통해 로그인 쿠키와 302 Location을 전달해야 합니다. API 호스트로 직접 돌아오거나 Authorization 헤더만 보관하는 클라이언트는 별도 세션 바인딩 없이 지원하지 않습니다.

Repository permissions: Metadata Read, Contents Read, Pull requests Read, Issues Read, Administration Read & write.
Webhook events: installation, installation_repositories, push, pull_request, pull_request_review, issues, member, repository.

### 비공개 Spring 설정

다음은 예시이며 실제 설정 파일·배포 시크릿에는 넣지 않았습니다. Mac 개발은 Mac 터미널/로컬 실행 설정, 실배포는 승인된 EC2/배포 시크릿 설정에서 각각 지정합니다. 비밀값을 채팅이나 Git 또는 프론트 공개 환경변수로 보내지 않습니다.

```yaml
integration:
  github:
    account:
      enabled: true
      client-id: ${GITHUB_ACCOUNT_CLIENT_ID}
      client-secret: ${GITHUB_ACCOUNT_CLIENT_SECRET}
      state-secret: ${GITHUB_ACCOUNT_STATE_SECRET}
      front-origin: ${GITHUB_ACCOUNT_FRONT_ORIGIN}
      callback-uri: ${GITHUB_ACCOUNT_CALLBACK_URI}
    app:
      enabled: true
      app-id: ${GITHUB_APP_ID}
      slug: ${GITHUB_APP_SLUG}
      private-key: ${GITHUB_APP_PRIVATE_KEY}
      webhook-secret: ${GITHUB_WEBHOOK_SECRET}
      sync-interval-ms: 3600000
    work-delay-ms: 5000
    work-initial-delay-ms: 10000
```

- Client ID/secret와 App ID/private key는 같은 GitHub App의 값이어야 합니다.
- private-key는 줄바꿈을 포함한 PEM 문자열입니다. 파일 경로가 아닙니다.
- state-secret은 로그인 JWT 키와 별개인 32바이트 이상 랜덤 키의 표준 Base64입니다.
- account.callback-uri는 위 User authorization callback URL 1입니다. 두 번째 주소는 front-origin에서 안전하게 구성합니다.
- 두 기능은 기본 비활성입니다. 프론트는 실제 검증 시 MSW를 끄고 동일 origin 프록시를 사용합니다.
- Redis 6.2 이상이 필요합니다. 1회용 state 저장/소비는 기존 Redis 데이터를 지우지 않고 별도 prefix를 사용합니다.

## DB와 배포 전 필수 확인

- 새 V6는 GitHub 연결·영속 작업·스냅샷·이벤트·PR·협업자·권한 이력·웹훅 영수증 테이블을 추가합니다. 기존 테이블을 삭제하거나 데이터를 비우지 않습니다.
- V3/V4/V5/V6는 모두 실서버 이력·기존 스키마·백업/복구와 적용 순서를 검토해야 합니다. 관리자만 먼저 배포하려 해도 현재 엔티티/후크의 GitHub 스키마 의존성을 함께 고려해야 합니다.
- H2 MySQL 호환 모드에서 실제 V5/V6 SQL의 기본 보존·유일성·FK 계약을 검사하지만, 이것은 실제 MySQL 적용 검증이 아닙니다.
- 2026-11-06 승인된 AWS CloudShell 격리 MySQL 8.0.46에서 서버 백업을 복원하여 V3~V6 원문 SQL, 기존 데이터 보존, 제약, 전체 Hibernate validate를 확인했습니다. 아래 추가 검증 기록을 참고합니다. 실제 서비스 DB를 검증용으로 사용하지 않았습니다.
- 서버 시간대/UTC 저장, 쿠키 SameSite·Secure, 프록시의 Referrer-Policy 및 인증 쿼리 로그 제외, App 권한·조직 정책·레이트 제한을 실제 환경에서 확인해야 합니다.
- 실제 OAuth 승인/설치/연결, 비공개 저장소 접근 제한, 실제 초대 수락/취소·회수, 웹훅 재전송, 프로세스 재시작 후 재시도를 별도로 확인해야 합니다.
- 위 확인과 승인 전에는 커밋·푸시·실서버 SQL 적용·배포를 하지 않습니다.

## 최종 격리 검증 결과 (게시 전 개발 검증 기록)

- 선별 테스트 **502개**, 실패 0, 오류 0, 건너뜀 0. Java 17 `bootJar` 성공.
- 계정 연결을 포함한 GitHub 관련 303개와 기존 관리자/인증/이미지 회귀 199개입니다. 전체 인수·외부 통합 테스트 실행을 의미하지 않습니다.
- Spring Security/MockMvc에서 공개·인증·리더/팀원 오류 전달, 추가 OAuth 콜백 쿠키 바인딩, 한국어 오류·명시적 null·캐시 정책을 확인했습니다.
- 실제 Hibernate/H2에서 영속 작업 커밋/롤백, 병렬 점유·임대·attempt 재점유 방어·재시도, 원본 계정 변경과 이벤트의 원자성, 초대/회수 이력·탈퇴 후 숫자 ID 회수·리더 보호·외부 실패 격리를 확인했습니다.
- 비공개 상태 no-op 웹훅에서도 실제 행 버전이 증가하는지, DB 기반 기여자 비노출과 서울 주간 집계를 확인했습니다.
- V5/V6 원문 SQL을 H2 MySQL 호환 모드의 가상 기존 행에 적용하여 기본 보존·유일성·FK 계약을 검사했습니다. 실제 MySQL에 대한 검증은 아닙니다.
- GitHub HTTP/OAuth는 대체 응답과 생성한 테스트 키만 사용했습니다. 실제 저장소에 초대·회수·웹훅을 보내지 않았습니다.
- 현재 소스·마이그레이션·GitHub 테스트가 검증 복사본과 일치함을 대조했습니다. 기존 build.gradle, GitHub 외 기존 테스트, 비공개 설정/리소스, 기존 업무 서비스의 미완료 코드는 작업 전과 바이트 단위로 동일합니다.
- 이 개발 검증 당시 게시된 main은 ffa7a98이었고 변경은 로컬 작업 트리에만 있었습니다. 이후 배포 전 추가 검증은 아래 기록과 구분합니다.

남은 실제 GitHub 검증은 **[blocked] GitHub App 설정 필요**입니다. 프론트 MSW를 끈 실제 OAuth/설치/초대/회수 E2E는 아직 완료하지 않았습니다. App 설정과 실검증 전에는 계정 연결과 App 기능을 기본 비활성 상태로 유지합니다.


## 2026-11-06 배포 전 추가 검증

- 게시된 `build.gradle`과 추적 리소스를 기준으로 별도 배포 후보를 만들고 Java 17 `bootJar -x test` 빌드 성공을 확인했습니다. 기존 미완료 빌드/인수 테스트 변경은 포함하지 않습니다.
- 서비스와 분리된 AWS CloudShell MySQL **8.0.46**에 서버의 비공개 백업을 복원했습니다. 복원 후 V3/V4/V5/V6 원문 SQL을 순서대로 적용했습니다.
- 기존 **20개 테이블의 모든 원래 컬럼 값**을 명시적으로 투영하여 전후 SHA-256을 비교했고 일치했습니다. 새 스키마는 총 30개 테이블입니다.
- 유일성 위반 3건과 외래키 위반 2건을 실제 MySQL에서 확인하고 테스트 트랜잭션을 롤백했습니다. 연결 세대가 다른 권한 이력은 함께 저장할 수 있음을 확인했습니다.
- 기존 프로젝트 삭제 상태의 visibility 매핑과 회원의 새 기본값 위반은 각각 0건이었습니다.
- 같은 배포 후보를 Java 17 앱과 별도 Redis로 실행했습니다. 전체 Hibernate `validate`, 앱 기동, DB/Redis를 포함한 `/actuator/health`의 `UP`, 미인증 `/api/v1/members/me`의 401을 확인했습니다.
- 격리 검증의 자격 증명은 임시 값이며 GitHub 계정/App 기능, SQL 초기화, Flyway 자동 실행, 테스트 계정 초기화, 외부 인증 warm-up은 비활성으로 실행했습니다. 실제 GitHub OAuth/설치/초대/회수 성공을 뜻하지 않습니다.
- 실제 서버의 수동 마이그레이션은 별도 백업과 배포 잠금을 사용합니다. 기존 Flyway 이력이 없으므로 자동 Flyway나 임의 baseline/history 생성으로 대체하지 않습니다. 적용 파일 체크섬과 데이터 비교 결과는 서버의 비공개 백업 디렉터리에 보관합니다.

### 같은 날 서비스 DB 준비 완료

사용자 배포 승인 후 별도 비공개 백업과 배포 잠금을 사용하여 실제 테스트 서버에 V3~V6를 순서대로 수동 반영했습니다. 원래 20개 테이블의 기존 컬럼 값 전후 해시가 일치했고, DB/Redis 컨테이너 ID·이미지와 `.env`·테스트 계정 설정·Compose 파일 체크섬도 그대로였습니다. 기존 앱 재기동 후 DB/Redis 포함 health `UP`을 확인했습니다. Flyway는 계속 비활성이며 임의 history/baseline을 생성하지 않았습니다.

관리자 권한은 임의 부여하지 않았습니다. 최초 관리자 계정 지정과 GitHub App 비공개 설정·실제 외부 연동 검증은 별도 작업입니다.
