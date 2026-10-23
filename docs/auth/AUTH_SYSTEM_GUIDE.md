# MeeTeam 인증/인가 시스템 가이드

> 기준: 공개 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`의 소스. 구현 변경이나 실행 검증 없이 문서를 대조했습니다.
> 이전 이메일 자체 인증 및 Google/GitHub OAuth2 설명은 현행 구현과 다릅니다. 로그인/온보딩 예시는 [세종대 로그인 흐름](../SEJONG_LOGIN_FLOW.md)을 사용하세요.

## 1. 현재 인증 흐름

1. `POST /api/v1/auth/login/sejong`: JSON `studentId`, `password`. 일반 계정은 실제 세종대 포털 인증 후 활성 회원을 조회합니다.
2. 기존 회원은 Access/Refresh 쿠키를 받고, 신규 회원은 10분짜리 Redis 일회용 회원가입 코드를 받습니다. 신규 응답 자체는 토큰을 발급하지 않습니다.
3. `POST /api/v1/auth/register/sejong`: multipart `request` JSON과 선택 `file`. 코드 소비 후 회원 생성 및 두 쿠키 설정. 회원가입 실패 시에도 이미 소비한 코드는 재사용할 수 없습니다.
4. 로그인 응답은 회원 프로필이 아닙니다. 프로필은 `GET /api/v1/members/me`로 별도 조회합니다.

성공 응답은 `code: "COMMON200"`, `message: "요청에 성공했습니다."`, 선택적 `result`입니다. null인 `result`는 생략됩니다. 오류 응답은 `code`, `message`입니다. `isSuccess`, `data`, JSON 토큰 또는 로그인 응답의 Authorization 헤더에 의존하지 마세요. 로그인 분기 속성은 DTO boolean getter의 기본 Jackson 직렬화 기준 `result.newMember`입니다. Java 필드명 `isNewMember`와 구별하세요.

세종대 회원의 내부 이메일은 학번에 `@sju.ac.kr`을 붙여 생성하며 JWT의 subject/username에도 사용합니다. 이는 이메일 자체 로그인이나 이메일 소유권 검증 기능이 아닙니다. 회원가입에서는 포털 비밀번호 대신 무작위 값의 BCrypt 해시를 저장합니다.

## 2. 쿠키 및 브라우저 요청

| 항목 | 현재 동작 |
|---|---|
| 이름 | `accessToken`, `refreshToken` |
| HttpOnly / Path | 둘 다 true / `/` |
| Secure / SameSite | `app.cookie.secure`, `app.cookie.same-site`; Java 기본값 true / None |
| Domain | `app.cookie.domain`이 비어 있지 않을 때만 설정. 생략하면 host-only |
| Max-Age | 각각 설정된 JWT 만료 시간(ms)을 1000으로 나눈 초 단위 |
| 삭제 | 동일 속성으로 빈 값과 Max-Age=0 설정 |

토큰을 localStorage에 복사하거나 JavaScript로 HttpOnly 쿠키/Set-Cookie를 읽지 않습니다. cross-origin fetch는 `credentials: 'include'`, Axios는 `withCredentials: true`를 사용하세요. 서버 CORS 허용 origin/credentials, HTTPS, 브라우저의 제3자 쿠키 정책도 맞아야 합니다. `SameSite=None`은 Secure가 필요하며 이것만으로 브라우저 차단을 해제하지 못합니다. 실제 배포 도메인, CORS, JWT 만료 시간은 이 문서에서 확정하지 않습니다.

## 3. JWT와 필터

- JWT 생성 claim: `jti`, `sub`, `category`(`access`/`refresh`), `username`(내부 이메일), `id`(회원 ID), `iat`, `exp`. 발급 메서드는 role claim을 추가하지 않으며 권한은 DB의 UserDetails에서 읽습니다.
- 보호 요청의 토큰 선택 순서는 `Authorization: Bearer ...` 우선, 없으면 `accessToken` 쿠키입니다. 잘못된 Bearer를 보내면 정상 쿠키로 다시 시도하지 않습니다.
- 필터는 서명/만료, JTI 블랙리스트, 활성 회원 조회 결과로 SecurityContext를 구성합니다. 현재 필터에 category가 access인지 검사하는 분기는 없습니다. 따라서 문서는 access 전용 검증이 구현되었다고 보장하지 않습니다.
- public 경로도 유효한 토큰이 있으면 사용자 정보를 설정하지만, 토큰 파싱 실패는 익명 요청으로 진행합니다. 나머지 경로는 인증이 필요합니다.
- SecurityConfig는 stateless이며 CSRF, HTTP Basic, form login을 끕니다. OAuth2 로그인 필터는 구성하지 않습니다.

### public과 권한 보장의 구별

`PublicEndpoint`는 `/api/v1/auth/**` 전체를 public으로 선언합니다. `/api/login`, `/api/register`가 whitelist에 남아 있어도 해당 로그인/가입 컨트롤러가 존재한다는 뜻은 아닙니다.

특히 `DELETE /api/v1/auth/delete/{memberId}`의 설명은 Admin 전용이지만 현재 컨트롤러/필터 체인에는 관리자 권한 검사 코드가 없습니다. `POST /api/v1/auth/register/{memberId}` 자기소개 등록도 principal/소유권 검사 없이 memberId로 처리합니다. 이 문서는 이를 안전한 관리자/본인 전용 API로 안내하지 않습니다. 별도 구현 검토가 필요한 사항이며 여기서는 코드를 변경하지 않았습니다.

## 4. 재발급, 로그아웃, 탈퇴

| API | 동작 |
|---|---|
| `POST /api/v1/auth/refresh` | refreshToken 쿠키만 읽고 만료/category/활성 회원을 확인하여 두 쿠키를 다시 발급. 성공 result는 `토큰이 갱신되었습니다.` |
| `POST /api/v1/auth/logout` | 헤더 우선 또는 accessToken 쿠키에서 토큰 추출, 유효한 access JTI를 남은 TTL 동안 Redis 블랙리스트에 추가, 두 쿠키 삭제. 성공 result는 `로그아웃이 완료되었습니다.` |
| `DELETE /api/v1/auth/withdraw` | principal의 회원을 soft delete, access 블랙리스트 처리, 두 쿠키 삭제. 성공 result는 `회원 탈퇴가 완료되었습니다.` |

- 재발급은 새 토큰 발급이라는 의미의 rotation입니다. 기존 refresh를 서버에 저장/폐기하거나 재사용을 탐지하는 로직은 해당 서비스에 없습니다. 로그아웃도 refresh 토큰 자체를 폐기하지 않으므로 탈취 refresh의 즉시 무효화를 보장하지 않습니다.
- refresh 쿠키 없음은 HTTP 401/TOKEN401, 만료는 401/TOKEN402, 잘못된 refresh는 401/TOKEN403, 활성 회원 없음은 401/MEMBER401입니다.
- public auth 경로라도 withdraw는 principal을 필요로 합니다. 현재 withdraw에는 null principal 명시 검사가 없으므로 미인증 요청의 정상적인 401 처리를 보장한다고 문서화하지 않습니다.
- soft delete는 deletedAt과 참여 가능 여부를 변경합니다. 로그인은 활성 학번만 조회하므로 탈퇴 회원은 신규 가입 흐름으로 갈 수 있습니다. 하드 삭제는 별도 API이며 위 권한 주의사항을 적용하세요.

## 5. 내 프로필

- `GET /api/v1/members/me`: principal이 없으면 UNAUTHORIZED. 성공 `result`에 name, memberId, birthDate, gender, email, githubUrl, blogUrl, representativePosition/representativePositionEn, skills, isParticipating, projectCount, introduce, profileImageUrl/profileImageName, projectCards를 반환합니다. 로그인 응답에서 이름/회원 ID/토큰을 받는 흐름이 아닙니다.
- `PUT /api/v1/members/me`: multipart `memberInfo` JSON + 선택 `profileImage`. 가입의 `request`/`file` 파트와 다릅니다.
- 수정 필수값: name(최대 50), age(1~150), gender, 비어 있지 않은 jobPositionIds와 techStacks(`id`, 1 이상 `displayOrder`). 선택값은 isParticipating, introduction(최대 1000), githubUrl, blogUrl입니다.
- 가입은 birthDate와 직군/직무 ENUM을 쓰지만 수정은 age와 직무 DB ID 목록을 씁니다. 수정은 age 필드를 바꾸며 birthDate를 다시 계산하지 않습니다. 조회 자기소개 필드명은 `introduce`, 수정 요청은 `introduction`입니다.
- 이미지를 생략하면 기존 이미지를 유지합니다. 직무/스택 목록은 기존 목록을 지우고 새 목록으로 교체합니다. 이미지 업로드는 S3 설정/권한이 별도로 필요합니다.

## 6. 제한된 비운영 테스트 계정

- TestAccountService/Controller는 `test & !prod & !production` 프로필과 `test-account.enabled=true`가 모두 충족될 때만 생성됩니다. test 프로필만으로 켜지지 않습니다.
- 비밀번호는 `test-account.password` 외부 설정이 필요하며 최소 16자, UTF-8 최대 72바이트입니다. 이 문서에는 실제 값을 기록하지 않습니다.
- 예약 사용자명은 `99990001`입니다. `POST /api/v1/auth/login/test` JSON의 username/password로 로그인하면 두 쿠키를 설정하고 result 없는 성공 응답을 반환합니다.
- 같은 예약 계정은 세종 로그인 JSON의 studentId/password로도 접근 가능합니다. 이때만 포털을 건너뛰고 기존 회원 로그인 결과를 반환합니다. 다른 모든 학번은 여전히 실제 포털 인증을 거칩니다.
- 프로세스 내 공유 제한은 분당 10회이며 성공도 포함합니다. 초과/불일치는 BAD_CREDENTIALS(401)입니다. 분산 rate limiter나 실제 대학 계정 우회 기능이 아닙니다.
- 시작 시 예약 내부 이메일 계정을 생성하거나 기존 값과 신원/해시가 충돌하면 실패합니다. 기존 계정을 덮어쓰지 않습니다. 계정은 일반 USER이며 관리자 권한을 얻지 않습니다.

## 7. 소스 근거

아래는 모두 위 커밋의 줄 번호입니다. 경로 기준은 `src/main/java/com/wardk/meeteam_backend/`입니다.

| 주제 | 파일 및 줄 |
|---|---|
| API/쿠키 발급 | `web/auth/controller/AuthController.java:35-85,88-167,174-191` |
| 로그인/가입/refresh/logout | `global/auth/service/AuthService.java:90-158,193-264,278-281,343-360` |
| 응답 | `global/response/SuccessResponse.java:10-21`, `SuccessCode.java:10`, `ErrorResponse.java:10-19`, `ErrorCode.java:26-35,46` |
| 쿠키 | `global/auth/cookie/AccessTokenCookieProvider.java:30-65`, `RefreshTokenCookieProvider.java:30-65`, `global/config/CookieProperties.java:32-46` |
| JWT | `global/util/JwtUtil.java:29-41,109-119,143-153` |
| 필터/활성 회원 | `global/auth/filter/JwtFilter.java:38-74,92-129,135-170`, `global/auth/service/CustomUserDetailsService.java:20-28` |
| 보안/public | `global/config/SecurityConfig.java:46-76,83-96`, `PublicEndpoint.java:32-35,53-60` |
| 회원 모델 | `domain/member/entity/Member.java:106-122,166-176,190-192` |
| 프로필 | `web/member/controller/MemberController.java:77-106`, `web/member/dto/request/MemberProfileUpdateRequest.java:21-58`, `web/member/dto/response/MemberProfileResponse.java:22-82`, `domain/member/service/MemberProfileServiceImpl.java:54-98,143-190` |
| 테스트 계정 | `global/auth/service/TestAccountService.java:23-40,49-95`, `web/auth/controller/TestAccountController.java:19-43`, `global/auth/service/AuthService.java:90-99` |

## 미확인 범위

실제 배포 커밋/환경변수, 활성 프로필, 쿠키/CORS 최종 설정, 포털 연결, DB 옵션/이미지 업로드 동작은 확인하지 않았습니다. JSON 이름은 소스 및 기본 Lombok/Jackson 규칙 기준이며 런타임 응답 검증은 수행하지 않았습니다. 보안상 주의사항은 발견 사실의 기록이지 구현 수정 완료를 뜻하지 않습니다.
