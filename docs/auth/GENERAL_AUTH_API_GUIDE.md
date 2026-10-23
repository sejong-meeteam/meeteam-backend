# [LEGACY] 일반 회원 이메일 로그인/회원가입 가이드

> **현행 API 안내가 아닙니다.** 공개 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`에서 이메일/비밀번호 자체 인증은 제거되었습니다.
> 신규 연동은 [세종대 로그인/온보딩](../SEJONG_LOGIN_FLOW.md), 공통 쿠키/재발급/프로필은 [현행 인증 시스템 가이드](AUTH_SYSTEM_GUIDE.md)를 사용하세요.

## 과거 설계와 현재 대체 기능

| 과거 문서의 기능/경로 (호출 금지) | 현재 |
|---|---|
| 이메일 로그인 `/api/auth/login` | 학번/포털 비밀번호로 `/api/v1/auth/login/sejong` |
| 이메일 회원가입 `/api/auth/register` | 포털 인증 후 코드로 `/api/v1/auth/register/sejong` |
| `/api/auth/refresh`, `/api/auth/logout` | `/api/v1/auth/refresh`, `/api/v1/auth/logout` |
| Authorization 응답 헤더/JSON 토큰 저장 | HttpOnly accessToken/refreshToken 쿠키 |
| 로그인 응답의 회원 프로필 | 별도 `/api/v1/members/me` 조회 |

이전 가이드는 이메일/비밀번호 검증, 멀티파트 가입, 토큰 보관과 silent refresh를 설명했습니다. 재발급/로그아웃 개념은 유지되지만 경로, 응답 구조, 토큰 전달 방식이 달라 이전 복사용 fetch/Axios 코드와 에러 예시는 제거했습니다. 일반 이메일 인증이 테스트 계정 기능으로 복구된 것은 아닙니다. 테스트 계정은 명시적으로 활성화한 비운영 환경의 예약 계정 1개에 한정됩니다.

## 근거

위 커밋의 `src/main/java/com/wardk/meeteam_backend/` 기준:

- `global/auth/service/AuthService.java:50-53,90-115`: 자체/OAuth2 로그인 제거, 세종 인증 및 제한된 테스트 계정 분기.
- `web/auth/controller/AuthController.java:35-85,98-127`: 현재 로그인/가입/재발급/로그아웃 매핑과 쿠키 발급.
- `web/member/controller/MemberController.java:77-106`: 현재 내 프로필 조회/수정.
- `global/response/SuccessResponse.java:10-21`: code/message/result 구조. null result는 생략.

Whitelist의 오래된 경로가 컨트롤러 지원을 의미하지 않습니다. 과거 이메일 자체 로그인이나 이전 API 별칭의 동작을 가정하지 마세요.
