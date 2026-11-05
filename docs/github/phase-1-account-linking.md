# GitHub 계정 연결 1단계

> 이 문서는 계정 연결만 작성했을 때의 검증 기록입니다. 이후 저장소·활동·웹훅·협업자·알림 코드까지 구현했으며, 현재 범위와 적용 전 확인 사항은 [전체 연동 안내](full-integration.md)를 기준으로 봅니다.

## 현재 범위

로컬에서 계정 연결·조회·해제와 OAuth 콜백을 구현했습니다. GitHub 로그인을 추가한 것이 아니며, MeeTeam 로그인은 기존 세종 포털 인증을 그대로 사용합니다.

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/v1/members/me/github` | 연결 정보 또는 명시적인 `result: null` |
| GET | `/api/v1/members/me/github/authorize-url?redirectPath=/settings` | `result.authorizeUrl` |
| DELETE | `/api/v1/members/me/github` | 명시적인 `result: null` |
| GET | `/api/v1/github/oauth/callback` | 성공/실패 시 프론트 내부 경로로 302 |

- 계정 응답: `login`, `avatarUrl`, `profileUrl`, `connectedAt`. 연결 시각은 `+09:00` ISO-8601입니다.
- 계정 식별은 변경 가능한 login이 아니라 GitHub 숫자 ID를 사용합니다.
- 같은 GitHub 계정은 다른 회원에게 중복 연결할 수 없습니다. 같은 회원의 재연결은 갱신합니다.
- 계정 해제는 GitHub 저장소 권한이나 GitHub App 승인 자체를 취소하지 않습니다.
- 일반/관리자 회원 탈퇴 시 연결 정보를 같은 DB 트랜잭션에서 삭제합니다.
- GitHub 설정이 없어도 애플리케이션은 시작합니다. 인가 주소·콜백은 설정 누락 시 503 `GITHUB_NOT_CONFIGURED`로 차단됩니다. 조회·해제는 계속 가능합니다.

## OAuth 보안

- 별도 비밀키로 HS256 서명한 state, 계정 연결 전용 purpose, 10분 만료, 1회용 랜덤 nonce.
- PKCE S256. verifier는 state에 넣지 않고 Redis에 최대 10분 보관합니다.
- Redis `SET NX`와 원자적 `GETDEL` 사용. Redis 6.2 이상이 필요합니다.
- callback은 state의 회원과 **현재 MeeTeam 로그인 회원**이 같아야 합니다. 서명된 state만으로 계정을 연결하지 않습니다.
- `authVersion` 재검증으로 정지·정지 해제·권한 변경·탈퇴 전의 요청을 거부합니다.
- 별도 `githubLinkVersion`을 잠근 회원 행에서 검증/증가합니다. 연결 해제나 먼저 완료된 연결 뒤에는 오래된 인가 주소로 계정을 덮어쓰거나 다시 연결할 수 없습니다. MeeTeam 로그인 토큰을 해제 때문에 폐기하지는 않습니다.
- GitHub 네트워크 호출은 DB 변경 트랜잭션 밖에서 수행합니다. 실제 연결 변경과 유일성 검사는 한 트랜잭션입니다.
- redirectPath는 내부 경로, 최대 UTF-8 1024바이트만 허용합니다. 외부 URL, `//`, 백슬래시, 제어 문자와 위험한 인코딩은 거부합니다. 기존 쿼리를 보존하고 `github` 결과 파라미터는 교체합니다.
- GitHub access/refresh token은 DB·Redis·응답에 저장하지 않습니다. 사용자 확인 직후 폐기합니다.
- OAuth 관련 컨트롤러·서비스는 기존 인자/반환값 자동 로깅에서 제외합니다. 예외에 외부 응답이나 인증정보를 포함하지 않습니다.
- GitHub 호스트는 고정이며 HTTP 리다이렉트를 따라가지 않습니다. 연결/응답 타임아웃은 10초입니다.
- 응답은 `Cache-Control: no-store`, 콜백은 `Referrer-Policy: no-referrer`를 사용합니다. 실제 프록시/호스팅에서도 OAuth 쿼리와 인증 헤더를 로그에 남기지 않아야 합니다.

## 프론트 계약의 보안 보완: 콜백 주소

첨부 명세의 단순 API 호스트 콜백 대신, 현재 프론트 구조에서는 **프론트와 동일한 origin의 프록시 주소**를 등록해야 합니다.

```
https://{실제-프론트-호스트}/api/v1/github/oauth/callback
```

확인한 프론트 `app/api/v1/[...path]/route.ts` → `app/api/upstreamProxy.ts`는 로그인 쿠키를 백엔드에 전달하고, `redirect: manual`로 받은 302와 Location을 브라우저에 전달합니다. 이 경로를 사용해야 프론트 호스트의 HttpOnly 로그인 쿠키로 콜백 회원을 재검증할 수 있습니다. `FRONT_ORIGIN`과 콜백의 scheme/host/port는 코드에서도 일치해야 합니다.

프록시 없는 API 직접 연동이나 Authorization 헤더만 보관하는 클라이언트는 별도 브라우저 세션 바인딩 없이 지원하지 않습니다. 콜백에 유효한 로그인 쿠키가 없으면 연결은 실패합니다. 로그인 만료 시 다시 로그인한 뒤 새 인가 주소로 재시도합니다. 보안 검증을 삭제하여 우회하지 않습니다.

실배포 전에는 프록시의 `Referrer-Policy` 전달/전역 설정, 로그인 쿠키의 SameSite·Secure 정책, GitHub에서 돌아온 실제 top-level 탐색을 확인해야 합니다. 현재 프론트 프록시는 Location과 쿠키는 전달하지만 `Referrer-Policy`는 직접 전달하지 않습니다.

## GitHub App 등록·환경 설정

이번 단계에는 GitHub App의 Client ID, Client secret만 GitHub 인증에 필요합니다. App ID/private key와 webhook secret은 다음 저장소 연동 단계에 필요하며 이번 구현에서 사용하지 않습니다. OAuth App을 별도로 만들 필요는 없습니다.

1. GitHub App의 User authorization callback URL을 위 **실제 프론트 프록시 주소**로 등록합니다. 로컬 브라우저 확인용은 `http://localhost:3000/api/v1/github/oauth/callback` 등으로 등록할 수 있습니다.
2. `Request user authorization during installation`은 첨부 명세대로 끕니다. 설치/setup/webhook 기능은 아직 구현하지 않았으므로 이번 단계에서 활성화하지 않습니다.
3. 다음 Spring 설정을 승인된 로컬/서버 비공개 설정이나 배포 시크릿으로 제공합니다. 비밀값을 채팅·Git·프론트 공개 환경변수에 넣지 않습니다.

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
```

위 블록은 **설정 예시**이며 실제 비공개 설정에는 아직 추가하지 않았습니다. 예시 환경변수 이름은 이 블록과 함께 사용할 때 매핑됩니다. 설정 파일 없이 Spring 직접 환경변수 바인딩을 사용한다면 `INTEGRATION_GITHUB_ACCOUNT_ENABLED`, `INTEGRATION_GITHUB_ACCOUNT_CLIENTID`, `INTEGRATION_GITHUB_ACCOUNT_CLIENTSECRET`, `INTEGRATION_GITHUB_ACCOUNT_STATESECRET`, `INTEGRATION_GITHUB_ACCOUNT_FRONTORIGIN`, `INTEGRATION_GITHUB_ACCOUNT_CALLBACKURI`입니다.

- state-secret은 새로 생성한 32바이트 이상 랜덤 키의 표준 Base64입니다. MeeTeam 로그인 JWT 키나 GitHub client secret을 재사용하지 않습니다.
- Mac 로컬 개발은 **Mac 터미널/로컬 실행 설정**, 실제 배포는 **EC2/배포 시크릿 설정**에서 각각 지정합니다. EC2 값을 Mac 전역 설정에 복사하지 않습니다.
- front-origin은 HTTPS origin만 허용하며, 로컬 개발의 `http://localhost`와 `http://127.0.0.1`만 예외입니다. 경로·쿼리·fragment·userinfo는 넣지 않습니다.
- callback-uri는 같은 origin의 정확한 `/api/v1/github/oauth/callback`이어야 합니다.
- 실제 Client secret이나 state-secret은 아직 생성·등록·저장하지 않았습니다.

공식 참고:
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-user-authorization-callback-url

## DB 적용

새 `V5__github_account_link.sql`은 다음을 추가합니다.

- member의 `github_link_version BIGINT NOT NULL DEFAULT 0`.
- `member_github_account`: 회원 PK/FK, GitHub ID UNIQUE, login/avatar/connected_at. 회원의 물리 삭제는 FK cascade입니다.

기존 필드나 데이터를 삭제하는 SQL은 없습니다. 현재 코드의 탈퇴 후크도 이 테이블을 사용하므로, 관리자만 배포하려는 경우에도 새 의존성을 포함하여 적용 순서를 검토해야 합니다.

**실서버 DB에는 적용하지 않았습니다.** 실제 스키마·Flyway 이력·백업/복구 계획을 확인한 뒤 V3/V4/V5 적용을 별도로 승인해야 합니다. 운영에서 Hibernate 자동 변경으로 검증을 대체하지 않습니다.

## 로컬 검증

- 실제 작업 트리 대신 임시 복사본에서 선별 테스트 **361개, 실패 0, 오류 0, 건너뜀 0**과 bootJar가 통과했습니다.
- 새 GitHub 테스트 162개 + 기존 관리자/인증/이미지 회귀 테스트 199개입니다. 전체 인수·통합 테스트를 통과했다는 뜻은 아닙니다.
- 보안 테스트: 비로그인 차단, HttpOnly 쿠키로 callback 회원 전달, 폐기된 토큰의 회원 바인딩 차단, 명시적 null 응답, no-store.
- OAuth: state 변조/만료/목적·알고리즘·필드 오류/재사용, nonce TTL·원자적 소비, PKCE, 브라우저 회원 불일치, 거부/실패, 외부 응답 오류, 중복 연결, 로그 비노출.
- H2 저장소 테스트: 조회·재연결·login 변경·중복 연결·경합·해제·이전 요청 무효화·정지·탈퇴. 이는 실제 MySQL SQL 적용 검증이 아닙니다.
- 별도 실제 Redis 7.2.7을 빈 임시 인스턴스로 실행하여 운영용 state 서비스/저장소를 직접 검증했습니다. TTL 최대 600초, verifier만 저장, 20개 동시 callback 소비 중 정확히 1개 성공, nonce 삭제·재사용 거부를 확인했습니다. 검증 후 이 인스턴스만 종료했고 영속 데이터 파일은 만들지 않았습니다. 기존 Redis와 DB는 사용하지 않았습니다.
- 기존 `build.gradle`과 기존 테스트 파일은 수정하지 않았습니다. 격리 빌드는 게시된 Gradle 설정에 JUnit 호환성만 별도로 보정하고, 현재 작업 중인 인수 테스트는 제외했습니다.
- V5 실제 MySQL 검증은 **[blocked] Docker 설정/소켓 접근 권한 필요**. 별도 검증 하네스와 baseline/current 컴파일까지만 준비했으며 MySQL 테스트를 실행하거나 보존 행 수를 측정하지 못했습니다. 컨테이너나 실데이터는 생성·수정하지 않았습니다.

## 아직 구현·검증하지 않은 것

- 실제 GitHub App 등록과 비밀값 설정, 실제 GitHub OAuth 승인 및 프론트 연결/해제 화면 E2E.
- V5 실제 MySQL 적용, 스키마 validate, 기존 데이터 보존 및 FK cascade 동작 확인.
- App 설치/setup, 프로젝트-저장소 연결, 활동 배지·집계/동기화, webhook, PR 진행 현황, 주간 리포트.
- 저장소 자동 초대·권한 회수·알림. 계정 연결 직후의 자동 초대도 다음 단계이며 현재 호출하지 않습니다.
- 커밋·푸시·배포. 변경은 로컬 작업 트리에만 있습니다.
