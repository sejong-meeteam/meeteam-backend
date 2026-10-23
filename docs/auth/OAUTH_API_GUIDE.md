# [LEGACY] OAuth API 가이드

> **제거된 과거 설계입니다. 실행/연동 안내로 사용하지 마세요.** 공개 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`에서는 Google/GitHub OAuth2 로그인을 지원하지 않습니다.
> 현재 지원 흐름은 [세종대 로그인/회원가입](../SEJONG_LOGIN_FLOW.md)이며 [공통 인증 가이드](AUTH_SYSTEM_GUIDE.md)에서 쿠키/재발급/프로필을 확인할 수 있습니다.

## 역사적 설계 요약 (현재 미지원)

과거에는 소셜 제공자로 이동한 뒤 콜백에서 임시 코드를 Redis에 저장하고, 프론트엔드 리다이렉트의 code/type으로 기존 회원 토큰 교환과 신규 회원 온보딩을 분기했습니다. 임시 코드는 URL에 JWT를 직접 노출하지 않기 위한 설계였습니다.

다음은 제거된 기능의 식별자이며 호출 지침이 아닙니다.

- Google/GitHub의 `/oauth2/authorization/{provider}` 진입 및 OAuth2 콜백/리다이렉트 처리
- 과거 `/api/auth/token/exchange` 토큰 교환
- 과거 `/api/auth/register/oauth2` 신규 회원 가입

혼동을 피하기 위해 소셜 버튼 코드, 리다이렉트 파싱, 복사용 API 요청과 응답 예시는 제거했습니다. 현재 세종 로그인은 리다이렉트 없이 JSON 요청을 받습니다. 기존 회원은 즉시 쿠키를 받고, 신규 회원만 Redis 일회용 코드를 받아 세종 가입 API로 제출합니다. 이 코드는 OAuth2 토큰 교환용 코드가 아닙니다.

## 근거

위 커밋의 `src/main/java/com/wardk/meeteam_backend/` 기준:

- `global/config/SecurityConfig.java:26-29,46-76`: OAuth2/자체 인증 제거 및 JWT 필터 체인.
- `global/auth/service/AuthService.java:50-53,90-151`: 현재 세종 로그인/온보딩 서비스.
- `web/auth/controller/AuthController.java:35-85`: 세종 API 매핑, 신규 코드 반환 및 기존 회원 쿠키 설정.
- `global/auth/repository/SejongCodeRepository.java:27-41,56-69`: 세종 가입 코드 10분 TTL 및 원자적 소비.

실제 배포에서 과거 URL이 어떤 HTTP 응답을 주는지는 확인하지 않았습니다. 라우팅/프록시와 관계없이 위 커밋은 해당 OAuth2 인증 흐름을 제공하지 않습니다.
