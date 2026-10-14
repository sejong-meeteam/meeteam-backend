# MeeTeam 인증/인가 시스템 가이드

## 목차

1. [전체 구조 개요](#1-전체-구조-개요)
2. [일반 로그인 플로우](#2-일반-로그인-플로우)
3. [OAuth2 로그인 플로우](#3-oauth2-로그인-플로우)
4. [JWT 토큰 구조](#4-jwt-토큰-구조)
5. [JwtFilter 동작](#5-jwtfilter-동작)
6. [토큰 재발급](#6-토큰-재발급)
7. [로그아웃](#7-로그아웃)
8. [SecurityConfig 설정](#8-securityconfig-설정)
9. [주요 클래스 정리](#9-주요-클래스-정리)

---

## 1. 전체 구조 개요

### 인증 방식

```
┌─────────────────────────────────────────────────────────────────┐
│                        MeeTeam 인증 시스템                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────────────┐          ┌──────────────┐                    │
│   │  일반 로그인   │          │  OAuth2 로그인 │                    │
│   │  (이메일/PW)  │          │ (Google/GitHub)│                    │
│   └──────┬───────┘          └───────┬──────┘                    │
│          │                          │                            │
│          ▼                          ▼                            │
│   ┌──────────────┐          ┌──────────────┐                    │
│   │  LoginFilter │          │OAuth2UserService│                   │
│   └──────┬───────┘          └───────┬──────┘                    │
│          │                          │                            │
│          └──────────┬───────────────┘                            │
│                     ▼                                            │
│            ┌──────────────┐                                      │
│            │   JwtUtil    │  ← AccessToken + RefreshToken 발급   │
│            └──────┬───────┘                                      │
│                   ▼                                              │
│            ┌──────────────┐                                      │
│            │  JwtFilter   │  ← 모든 요청에서 토큰 검증            │
│            └──────────────┘                                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 토큰 구조

| 토큰 | 만료 시간 | 저장 위치 | 용도 |
|------|----------|----------|------|
| AccessToken | 10시간 | 클라이언트 (메모리/sessionStorage) | API 인증 |
| RefreshToken | 7일 | HttpOnly Cookie | AccessToken 재발급 |
| OAuth 일회용 코드 | 60초(로그인) / 10분(회원가입) | Redis | OAuth2 인증 후 토큰 교환용 |

---

## 2. 일반 로그인 플로우

### 시퀀스 다이어그램

```
┌────────┐          ┌────────────┐          ┌──────────┐          ┌────┐
│클라이언트│          │ LoginFilter │          │ AuthManager│          │ DB │
└───┬────┘          └─────┬──────┘          └────┬─────┘          └─┬──┘
    │                     │                      │                   │
    │ POST /api/auth/login│                      │                   │
    │ {email, password}   │                      │                   │
    │────────────────────>│                      │                   │
    │                     │                      │                   │
    │                     │ authenticate()       │                   │
    │                     │─────────────────────>│                   │
    │                     │                      │                   │
    │                     │                      │ findByEmail()     │
    │                     │                      │──────────────────>│
    │                     │                      │                   │
    │                     │                      │<──────────────────│
    │                     │                      │  Member           │
    │                     │                      │                   │
    │                     │                      │ 비밀번호 검증      │
    │                     │                      │                   │
    │                     │<─────────────────────│                   │
    │                     │ Authentication       │                   │
    │                     │                      │                   │
    │                     │ 토큰 생성            │                   │
    │                     │ - AccessToken        │                   │
    │                     │ - RefreshToken       │                   │
    │                     │                      │                   │
    │<────────────────────│                      │                   │
    │ 응답:               │                      │                   │
    │ - Header: Authorization: Bearer {access}   │                   │
    │ - Cookie: refreshToken (HttpOnly)          │                   │
    │ - Body: {memberId, name}                   │                   │
```

### 관련 코드

**LoginFilter.java** - `attemptAuthentication()`
```java
// 1. JSON 요청 파싱
LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);

// 2. Spring Security 토큰 생성
UsernamePasswordAuthenticationToken authToken =
    new UsernamePasswordAuthenticationToken(username, password, null);

// 3. AuthenticationManager로 검증 (DB 조회 + 비밀번호 비교)
return authenticationManager.authenticate(authToken);
```

**LoginFilter.java** - `successfulAuthentication()`
```java
// 1. 토큰 생성
String accessToken = jwtUtil.createAccessToken(member);
String refreshToken = jwtUtil.createRefreshToken(member);

// 2. AccessToken → 응답 헤더
response.setHeader("Authorization", "Bearer " + accessToken);

// 3. RefreshToken → HttpOnly 쿠키
ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
    .httpOnly(true)
    .secure(false)  // 로컬: false, 프로덕션: true
    .domain(".meeteam.alom-sejong.com")
    .sameSite("None")
    .maxAge(refreshExpTime / 1000)
    .build();
response.addHeader("Set-Cookie", cookie.toString());
```

---

## 3. OAuth2 로그인 플로우

### 시퀀스 다이어그램 (클라이언트 / 백엔드 / OAuth 서버 / Redis)

```
┌──────────┐       ┌──────────────┐       ┌──────────────┐       ┌───────┐
│ 클라이언트 │       │ MeeTeam 서버  │       │ OAuth 서버    │       │ Redis │
│ (브라우저) │       │  (백엔드)     │       │(Google/GitHub)│       │       │
└────┬─────┘       └──────┬───────┘       └──────┬───────┘       └───┬───┘
     │                    │                      │                    │
     │ ① GET /oauth2/authorization/google       │                    │
     │───────────────────>│                      │                    │
     │                    │                      │                    │
     │ ② 302 Redirect     │                      │                    │
     │ Location: https://accounts.google.com/...│                    │
     │<───────────────────│                      │                    │
     │                    │                      │                    │
     │ ③ GET https://accounts.google.com/oauth/authorize             │
     │──────────────────────────────────────────>│                    │
     │                    │                      │                    │
     │ ④ 로그인 페이지 표시                       │                    │
     │<──────────────────────────────────────────│                    │
     │                    │                      │                    │
     │ ⑤ 사용자 로그인 + 동의                    │                    │
     │──────────────────────────────────────────>│                    │
     │                    │                      │                    │
     │ ⑥ 302 Redirect (인가 코드)               │                    │
     │ Location: {백엔드}/login/oauth2/code/google?code=auth_code    │
     │<──────────────────────────────────────────│                    │
     │                    │                      │                    │
     │ ⑦ GET /login/oauth2/code/google?code=auth_code                │
     │───────────────────>│                      │                    │
     │                    │                      │                    │
     │                    │ ⑧ POST /oauth/token  │                    │
     │                    │ (인가코드로 토큰 교환) │                    │
     │                    │─────────────────────>│                    │
     │                    │                      │                    │
     │                    │ ⑨ OAuth Access Token │                    │
     │                    │<─────────────────────│                    │
     │                    │                      │                    │
     │                    │ ⑩ GET /userinfo      │                    │
     │                    │─────────────────────>│                    │
     │                    │                      │                    │
     │                    │ ⑪ 사용자 정보        │                    │
     │                    │ {email, name, id}    │                    │
     │                    │<─────────────────────│                    │
     │                    │                      │                    │
     │                    │ ⑫ DB 조회 (기존/신규)│                    │
     │                    │                      │                    │
     │                    │ ⑬ 일회용 UUID 코드 생성 + 회원정보 저장   │
     │                    │─────────────────────────────────────────>│
     │                    │                      │                    │
     │ ⑭ 302 Redirect     │                      │                    │
     │ Location: {프론트}/oauth2/redirect        │                    │
     │   ?code=uuid&type=login (기존회원)        │                    │
     │   ?code=uuid&type=register (신규회원)     │                    │
     │<───────────────────│                      │                    │
     │                    │                      │                    │
     │ ⑮ POST /api/auth/token/exchange (기존회원)│                    │
     │    또는 POST /api/auth/register/oauth2 (신규회원)              │
     │ { code: "uuid" }   │                      │                    │
     │───────────────────>│                      │                    │
     │                    │                      │                    │
     │                    │ ⑯ 코드로 정보 조회 (조회 후 즉시 삭제)    │
     │                    │─────────────────────────────────────────>│
     │                    │                      │                    │
     │                    │<─────────────────────────────────────────│
     │                    │ 회원 정보 반환        │                    │
     │                    │                      │                    │
     │                    │ ⑰ JWT 토큰 생성      │                    │
     │                    │                      │                    │
     │ ⑱ 응답:            │                      │                    │
     │ { accessToken }    │                      │                    │
     │ + Set-Cookie: refreshToken                │                    │
     │<───────────────────│                      │                    │
```

### 단계별 상세 설명

| 단계 | 누가 → 누구 | 설명 |
|------|------------|------|
| ① | 클라이언트 → 백엔드 | 로그인 버튼 클릭 (브라우저 주소창 이동) |
| ② | 백엔드 → 클라이언트 | OAuth 서버로 리다이렉트 응답 (302) |
| ③ | 클라이언트 → OAuth | 브라우저가 자동으로 Google/GitHub 이동 |
| ④ | OAuth → 클라이언트 | 로그인 페이지 HTML 응답 |
| ⑤ | 클라이언트 → OAuth | 사용자가 로그인 + 동의 |
| ⑥ | OAuth → 클라이언트 | 백엔드 콜백 URL로 리다이렉트 (**인가코드** 포함) |
| ⑦ | 클라이언트 → 백엔드 | 브라우저가 자동으로 콜백 URL 호출 |
| ⑧ | 백엔드 → OAuth | **Spring Security가 자동으로** 인가 코드로 토큰 교환 |
| ⑨ | OAuth → 백엔드 | OAuth Access Token 응답 |
| ⑩ | 백엔드 → OAuth | **Spring Security가 자동으로** 사용자 정보 요청 |
| ⑪ | OAuth → 백엔드 | 사용자 정보 응답 (email, name 등) |
| ⑫ | 백엔드 내부 | DB 조회 (기존/신규 회원 판별) |
| ⑬ | 백엔드 → Redis | **일회용 UUID 코드 생성** + 회원 정보 저장 |
| ⑭ | 백엔드 → 클라이언트 | 프론트엔드로 리다이렉트 (`?code=uuid&type=...`) |
| ⑮ | 클라이언트 → 백엔드 | **토큰 교환 API 호출** (code 전달) |
| ⑯ | 백엔드 → Redis | 코드로 정보 조회 후 **즉시 삭제** (일회성 보장) |
| ⑰ | 백엔드 내부 | JWT AccessToken + RefreshToken 생성 |
| ⑱ | 백엔드 → 클라이언트 | 토큰 응답 (AccessToken + RefreshToken 쿠키) |

### Redis 일회용 코드를 사용하는 이유

| 방식 | 문제점 |
|-----|--------|
| `?accessToken=xxx` (URL에 JWT 노출) | URL 히스토리, 로그, Referer 헤더에 토큰 노출 위험 |
| `?code=uuid` (일회용 코드) | 짧은 TTL + 1회 사용 후 삭제 → **보안 강화** |

### Redis 저장 정보

| 회원 유형 | 저장 정보 | TTL | 교환 API |
|---------|---------|-----|---------|
| 기존 회원 | `OAuthLoginInfo` (memberId, oauthAccessToken) | 60초 | `POST /api/auth/token/exchange` |
| 신규 회원 | `OAuthRegisterInfo` (email, provider, providerId, oauthAccessToken) | 10분 | `POST /api/auth/register/oauth2` |

### 전체 흐름 요약

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           OAuth2 로그인 흐름                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. 로그인 버튼 클릭                                                      │
│     GET /oauth2/authorization/google                                     │
│            │                                                             │
│            ▼                                                             │
│  2. Google 로그인 페이지로 리다이렉트                                      │
│     https://accounts.google.com/oauth/authorize?...                      │
│            │                                                             │
│            ▼                                                             │
│  3. 사용자 동의 후 콜백                                                   │
│     GET /login/oauth2/code/google?code=xxx                               │
│            │                                                             │
│            ▼                                                             │
│  4. Spring Security가 자동으로:                                           │
│     - 인가 코드로 OAuth 액세스 토큰 교환                                   │
│     - 사용자 정보 요청                                                    │
│            │                                                             │
│            ▼                                                             │
│  5. CustomOidcUserService.loadUser() 호출                                │
│     → OAuth2UserProcessor.process()                                      │
│            │                                                             │
│            ├─────────────────────┬─────────────────────┐                 │
│            ▼                     ▼                     ▼                 │
│     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐          │
│     │ DB 조회       │     │ 기존 회원     │     │ 신규 회원     │          │
│     │              │     │ isNewMember  │     │ isNewMember  │          │
│     │              │     │ = false      │     │ = true       │          │
│     └──────────────┘     └──────┬───────┘     └──────┬───────┘          │
│                                 │                     │                  │
│                                 ▼                     ▼                  │
│  6. OAuth2AuthenticationSuccessHandler                                   │
│            │                                                             │
│            ├─────────────────────┬─────────────────────┐                 │
│            ▼                     ▼                                       │
│     ┌──────────────┐     ┌──────────────┐                               │
│     │ 기존 회원     │     │ 신규 회원     │                               │
│     │              │     │              │                               │
│     │ Redis에      │     │ Redis에      │                               │
│     │ OAuthLoginInfo│    │ OAuthRegisterInfo│                            │
│     │ 저장 (60초)  │     │ 저장 (10분)   │                               │
│     └──────┬───────┘     └──────┬───────┘                               │
│            │                     │                                       │
│            ▼                     ▼                                       │
│  7. 프론트엔드로 리다이렉트 (일회용 코드 전달)                              │
│     /oauth2/redirect       /oauth2/redirect                              │
│     ?code=uuid             ?code=uuid                                    │
│     &type=login            &type=register                                │
│            │                     │                                       │
│            ▼                     ▼                                       │
│  8. 프론트엔드가 토큰 교환 API 호출                                        │
│     POST /api/auth/       POST /api/auth/                               │
│     token/exchange        register/oauth2                                │
│     { code: "uuid" }      { code: "uuid", name, ... }                   │
│            │                     │                                       │
│            ▼                     ▼                                       │
│  9. 백엔드: Redis에서 정보 조회 후 삭제 (일회성 보장)                       │
│            │                     │                                       │
│            ▼                     ▼                                       │
│  10. AccessToken + RefreshToken(쿠키) 응답                               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 기존 회원 vs 신규 회원

| 구분 | 기존 회원 | 신규 회원 |
|------|---------|---------|
| DB 상태 | provider + providerId로 찾음 | 없음 |
| Role | USER | OAUTH2_GUEST |
| Redis 저장 | `OAuthLoginInfo` (60초 TTL) | `OAuthRegisterInfo` (10분 TTL) |
| 리다이렉트 | `?code=uuid&type=login` | `?code=uuid&type=register` |
| 토큰 교환 API | `POST /api/auth/token/exchange` | `POST /api/auth/register/oauth2` |
| 추가 절차 | 없음 | 추가 정보 입력 필요 |

### 관련 코드

**OAuth2UserProcessor.java**
```java
public OAuth2User process(String providerName, OAuth2User oAuth2User) {
    // 1. 속성 파싱 (Google vs GitHub 다름)
    ParsedUserInfo userInfo = parseAttributes(providerName, oAuth2User.getAttributes());

    // 2. DB에서 기존 회원 조회
    Optional<Member> memberOptional =
        memberRepository.findByProviderAndProviderId(providerName, userInfo.providerId());

    // 3. 기존 vs 신규 분기
    if (memberOptional.isPresent()) {
        // 기존 회원
        member = memberOptional.get();
        isNewMember = false;
    } else {
        // 신규 회원 - 임시 정보만 설정
        member = Member.builder()
            .email(userInfo.email())
            .provider(providerName)
            .providerId(userInfo.providerId())
            .role(UserRole.OAUTH2_GUEST)  // 임시 Role
            .build();
        isNewMember = true;
    }

    return new CustomOauth2UserDetails(member, attributes, isNewMember);
}
```

**OAuth2AuthenticationSuccessHandler.java** - Redis에 일회용 코드 저장 후 리다이렉트
```java
public void onAuthenticationSuccess(request, response, authentication) {
    CustomOauth2UserDetails userDetails = authentication.getPrincipal();
    Member member = userDetails.getMember();

    String redirectUrl;
    if (userDetails.isNewMember()) {
        // 신규 회원: Redis에 회원가입 정보 저장 (10분 TTL)
        OAuthRegisterInfo registerInfo = new OAuthRegisterInfo(
            member.getEmail(),
            member.getProvider(),
            member.getProviderId(),
            userDetails.getOauthAccessToken(),
            "register"
        );
        String code = oAuthCodeRepository.saveRegisterInfo(registerInfo);
        redirectUrl = redirectBaseUrl + "?code=" + code + "&type=register";
    } else {
        // 기존 회원: Redis에 로그인 정보 저장 (60초 TTL)
        OAuthLoginInfo loginInfo = new OAuthLoginInfo(
            member.getId(),
            userDetails.getOauthAccessToken(),
            "login"
        );
        String code = oAuthCodeRepository.saveLoginInfo(loginInfo);
        redirectUrl = redirectBaseUrl + "?code=" + code + "&type=login";
    }

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
}
```

**OAuthCodeRepository.java** - Redis 일회용 코드 관리
```java
@Repository
public class OAuthCodeRepository {
    private static final String KEY_PREFIX = "oauth:code:";
    private static final Duration REGISTER_TTL = Duration.ofMinutes(10);
    private static final Duration LOGIN_TTL = Duration.ofSeconds(60);

    // 코드 저장 (UUID 생성)
    public String saveLoginInfo(OAuthLoginInfo info) {
        String code = UUID.randomUUID().toString();
        String key = KEY_PREFIX + code;
        stringRedisTemplate.opsForValue().set(key, json, LOGIN_TTL);
        return code;
    }

    // 코드로 정보 조회 후 즉시 삭제 (일회성 보장)
    public Optional<OAuthLoginInfo> consumeLoginInfo(String code) {
        String key = KEY_PREFIX + code;
        String json = stringRedisTemplate.opsForValue().getAndDelete(key);  // 조회 + 삭제
        // ... 역직렬화 후 반환
    }
}
```

**AuthController.java** - 토큰 교환 API (기존 회원)
```java
@PostMapping("/token/exchange")
public SuccessResponse<TokenExchangeResponse> exchangeToken(
    HttpServletResponse response,
    @RequestBody TokenExchangeRequest request  // { code: "uuid" }
) {
    TokenExchangeResult result = authService.exchangeToken(request.getCode());

    // Refresh Token 쿠키 설정
    ResponseCookie cookie = ResponseCookie.from("refreshToken", result.getRefreshToken())
        .httpOnly(true).secure(true).path("/")
        .domain(".meeteam.alom-sejong.com").sameSite("None")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    return SuccessResponse.of(new TokenExchangeResponse(result.getAccessToken()));
}
```

### Google vs GitHub 속성 차이

**Google (OIDC)**
```json
{
  "sub": "12345678901234567890",  // providerId
  "email": "user@gmail.com",
  "name": "홍길동",
  "picture": "https://..."
}
```

**GitHub (OAuth2)**
```json
{
  "id": 12345678,                 // providerId (숫자)
  "email": "user@email.com",      // null일 수 있음!
  "name": "홍길동",
  "avatar_url": "https://..."
}
```

> GitHub 이메일이 null이면 `{providerId}@users.noreply.github.com` 형식으로 임시 생성

---

## 4. JWT 토큰 구조

### AccessToken

```
┌─────────────────────────────────────────────────────────────────┐
│                        AccessToken 구조                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Header (Base64)                                                 │
│  ┌────────────────────────────────────┐                         │
│  │ { "alg": "HS256", "typ": "JWT" }   │                         │
│  └────────────────────────────────────┘                         │
│                     .                                            │
│  Payload (Base64)                                                │
│  ┌────────────────────────────────────┐                         │
│  │ {                                  │                         │
│  │   "jti": "uuid-...",    ← 고유ID   │                         │
│  │   "sub": "user@email.com",         │                         │
│  │   "username": "user@email.com",    │                         │
│  │   "id": 1,              ← memberId │                         │
│  │   "category": "access",            │                         │
│  │   "iat": 1704067200,               │                         │
│  │   "exp": 1704103200     ← +10시간  │                         │
│  │ }                                  │                         │
│  └────────────────────────────────────┘                         │
│                     .                                            │
│  Signature                                                       │
│  ┌────────────────────────────────────┐                         │
│  │ HMACSHA256(header.payload, secret) │                         │
│  └────────────────────────────────────┘                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 토큰 타입별 category 값

| 토큰 타입 | category | 만료 시간 |
|----------|----------|----------|
| AccessToken | `"access"` | 10시간 |
| RefreshToken | `"refresh"` | 7일 |
| SignupToken | `"register"` | 50분 |

### JTI (JWT ID)의 역할

- 각 토큰의 **고유 식별자** (UUID)
- **블랙리스트**에서 로그아웃된 토큰 식별에 사용
- 같은 사용자가 여러 기기에서 로그인해도 각 토큰은 다른 JTI를 가짐

---

## 5. JwtFilter 동작

### 필터 처리 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                        JwtFilter 동작                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  HTTP 요청 수신                                                   │
│        │                                                         │
│        ▼                                                         │
│  ┌─────────────────────────────────────┐                        │
│  │ 화이트리스트 경로인가?               │                        │
│  │ (예: /api/auth/**, /api/main/**)   │                        │
│  └──────────────┬──────────────────────┘                        │
│                 │                                                │
│        ┌───────┴───────┐                                        │
│        ▼               ▼                                        │
│      YES              NO                                        │
│        │               │                                        │
│        │               ▼                                        │
│        │         Authorization 헤더 확인                         │
│        │         "Bearer {token}"                               │
│        │               │                                        │
│        │        ┌──────┴──────┐                                 │
│        │        ▼             ▼                                 │
│        │      있음           없음                                │
│        │        │             │                                 │
│        │        ▼             │                                 │
│        │  토큰 검증           │                                 │
│        │  ┌────────────┐     │                                 │
│        │  │ 1. 만료 확인│     │                                 │
│        │  │ 2. 블랙리스트│    │                                 │
│        │  │ 3. 서명 검증│     │                                 │
│        │  └─────┬──────┘     │                                 │
│        │        │             │                                 │
│        │   ┌────┴────┐       │                                 │
│        │   ▼         ▼       │                                 │
│        │ 유효      무효       │                                 │
│        │   │         │       │                                 │
│        │   ▼         │       │                                 │
│        │ SecurityContext     │                                 │
│        │ 에 인증 정보 설정    │                                 │
│        │   │         │       │                                 │
│        └───┼─────────┼───────┼──────────────────────────┐      │
│            ▼         ▼       ▼                          │      │
│      ┌─────────────────────────────────────────────┐    │      │
│      │              다음 필터로 진행                 │    │      │
│      └─────────────────────────────────────────────┘    │      │
│                                                         │      │
│  ※ 화이트리스트에서도 토큰이 있으면 인증 정보 설정 시도 ────┘      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 블랙리스트 체크

```java
// JwtFilter.processTokenAndSetUserDetails()

// 블랙리스트 확인 (로그아웃된 토큰인지)
String jti = jwtUtil.getJti(token);
if (jti != null && tokenBlacklistRepository.isBlacklisted(jti)) {
    log.warn("블랙리스트에 등록된 토큰입니다. JTI: {}", jti);
    return false;  // 인증 실패
}
```

### 화이트리스트 예시

```yaml
# application-local.yml
security:
  whitelist:
    - method: "*"
      uri: /api/auth/**           # 인증 관련
    - method: "*"
      uri: /login/oauth2/**       # OAuth2
    - method: GET
      uri: /api/main/**           # 메인 페이지
    - method: GET
      uri: /api/projects/**       # 프로젝트 조회
    - method: GET
      uri: /ws/**                 # WebSocket
```

---

## 6. 토큰 재발급

### 재발급 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                      토큰 재발급 흐름                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  POST /api/auth/refresh                                          │
│        │                                                         │
│        ▼                                                         │
│  ┌─────────────────────────────────────┐                        │
│  │ 헤더에 AccessToken 있고 유효한가?    │                        │
│  └──────────────┬──────────────────────┘                        │
│                 │                                                │
│        ┌───────┴───────┐                                        │
│        ▼               ▼                                        │
│       YES             NO                                        │
│        │               │                                        │
│        │               ▼                                        │
│        │         쿠키에서 RefreshToken 추출                      │
│        │               │                                        │
│        │        ┌──────┴──────┐                                 │
│        │        ▼             ▼                                 │
│        │      있음           없음                                │
│        │        │             │                                 │
│        │        ▼             ▼                                 │
│        │   RefreshToken     ERROR:                              │
│        │   검증            REFRESH_TOKEN_NOT_FOUND              │
│        │        │                                               │
│        │   ┌────┴────┐                                          │
│        │   ▼         ▼                                          │
│        │ 유효      만료/무효                                     │
│        │   │         │                                          │
│        │   │         ▼                                          │
│        │   │       ERROR:                                       │
│        │   │       REFRESH_TOKEN_EXPIRED                        │
│        │   │       or INVALID_REFRESH_TOKEN                     │
│        │   │                                                    │
│        │   ▼                                                    │
│        │ DB에서 사용자 존재 확인                                  │
│        │   │                                                    │
│        │   ▼                                                    │
│        │ 새 AccessToken 생성                                     │
│        │   │                                                    │
│        ▼   ▼                                                    │
│   기존/새 AccessToken 반환                                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 코드 흐름

```java
public String refreshAccessToken(HttpServletRequest request) {
    // 1. 기존 AccessToken이 유효하면 그대로 반환 (재발급 불필요)
    String existingAccessToken = extractAccessTokenFromHeader(request);
    if (existingAccessToken != null && !jwtUtil.isExpired(existingAccessToken)) {
        return existingAccessToken;
    }

    // 2. RefreshToken 추출
    String refreshToken = extractRefreshTokenFromCookies(request);
    if (refreshToken == null) {
        throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    // 3. RefreshToken 검증
    if (jwtUtil.isExpired(refreshToken)) {
        throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    // 4. 사용자 확인 후 새 AccessToken 발급
    String email = jwtUtil.getUsername(refreshToken);
    Member member = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    return jwtUtil.createAccessToken(member);
}
```

### 클라이언트 구현 가이드 (Silent Refresh)

API 요청 시 토큰 만료로 `401` 에러가 발생하면, 유저 개입 없이 자동으로 토큰을 재발급받고 원래 요청을 재시도하는 **Interceptor** 구현이 필요합니다.

**1. 로직 상세 흐름 (Axios Interceptor 권장)**

1.  **Request**: 클라이언트가 API 요청 전송
2.  **Error (401)**: 백엔드에서 `401 Unauthorized` 응답 (AccessToken 만료)
3.  **Catch**: Interceptor의 `responseError` 핸들러에서 401 에러 감지
4.  **Refresh**:
    *   즉시 `POST /api/auth/refresh` 요청 전송
    *   **주의**: `refreshToken`은 `HttpOnly Cookie`에 있으므로, 요청 시 `withCredentials: true` 설정 필수 (브라우저가 알아서 쿠키를 실어 보냄)
5.  **Retry**:
    *   **성공 시**: 응답받은 새 AccessToken으로 헤더를 갱신하고, **방금 실패했던 원래 요청(Original Request)을 새로운 토큰으로 재전송**
    *   **실패 시**: RefreshToken도 만료된 것이므로 **강제 로그아웃** 및 로그인 페이지 리다이렉트

**2. 구현 참고용 의사 코드 (Axios 예시)**

```javascript
// response interceptor
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // 401 에러이고, 아직 재시도를 안 했다면 (_retry 플래그 등 활용)
    if (error.response.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // 1. 토큰 재발급 요청 (쿠키 자동 전송됨)
        const { data } = await axios.post('/api/auth/refresh', {}, { withCredentials: true });
        
        // 2. 새 토큰 갈아끼우기
        const newAccessToken = data.result; // 응답 구조에 따라 경로 조정 필요
        axios.defaults.headers.common['Authorization'] = `Bearer ${newAccessToken}`;
        originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`;

        // 3. 원래 요청 재시도
        return axiosInstance(originalRequest);
      } catch (refreshError) {
        // 재발급 실패 시 로그아웃 처리
        alert('로그인이 만료되었습니다.');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);
```

---

## 7. 로그아웃

### 로그아웃 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                        로그아웃 흐름                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  POST /api/auth/logout                                           │
│  + Authorization: Bearer {accessToken}                           │
│        │                                                         │
│        ▼                                                         │
│  ┌─────────────────────────────────────┐                        │
│  │ AccessToken에서 JTI 추출            │                        │
│  └──────────────┬──────────────────────┘                        │
│                 │                                                │
│                 ▼                                                │
│  ┌─────────────────────────────────────┐                        │
│  │ Redis 블랙리스트에 JTI 등록         │                        │
│  │ TTL = 토큰 남은 만료 시간           │                        │
│  │                                     │                        │
│  │ Key: "blacklist:token:{jti}"        │                        │
│  │ Value: "1"                          │                        │
│  │ TTL: remainingTimeMs                │                        │
│  └──────────────┬──────────────────────┘                        │
│                 │                                                │
│                 ▼                                                │
│  ┌─────────────────────────────────────┐                        │
│  │ RefreshToken 쿠키 삭제              │                        │
│  │ (maxAge=0 쿠키 설정)                │                        │
│  └──────────────┬──────────────────────┘                        │
│                 │                                                │
│                 ▼                                                │
│  응답: "로그아웃이 완료되었습니다."                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

※ 이후 블랙리스트에 등록된 토큰으로 API 호출 시 → 인증 실패
※ Redis TTL 만료되면 블랙리스트에서 자동 제거 (토큰도 어차피 만료됨)
```

### 블랙리스트 동작

```java
// TokenBlacklistRepository.java

public void addToBlacklist(String jti, long remainingTimeMs) {
    String key = "blacklist:token:" + jti;
    stringRedisTemplate.opsForValue().set(key, "1", Duration.ofMillis(remainingTimeMs));
}

public boolean isBlacklisted(String jti) {
    String key = "blacklist:token:" + jti;
    return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
}
```

---

## 8. SecurityConfig 설정

### 필터 체인 순서

```
HTTP 요청
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. CorsFilter                                                   │
│     - CORS 프리플라이트 처리                                      │
│     - 허용된 Origin 확인                                          │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. JwtFilter (addFilterBefore)                                  │
│     - 토큰 검증                                                   │
│     - SecurityContext에 인증 정보 설정                            │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. LoginFilter (addFilterAt)                                    │
│     - POST /api/auth/login 요청 처리                             │
│     - 일반 로그인 인증                                            │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. OAuth2 필터들 (자동 등록)                                     │
│     - /oauth2/authorization/* 처리                               │
│     - /login/oauth2/code/* 콜백 처리                             │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  5. ExceptionTranslationFilter                                   │
│     - 인증/인가 예외 처리                                         │
│     - 401 → RestAuthenticationEntryPoint                         │
│     - 403 → RestAccessDeniedHandler                              │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
컨트롤러
```

### 주요 설정

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) {
    return http
        // CSRF 비활성화 (JWT 사용)
        .csrf(AbstractHttpConfigurer::disable)

        // HTTP Basic 비활성화
        .httpBasic(AbstractHttpConfigurer::disable)

        // Form 로그인 비활성화
        .formLogin(AbstractHttpConfigurer::disable)

        // STATELESS 세션 (세션 사용 안 함)
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )

        // 인증 예외 처리
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(restAuthenticationEntryPoint)  // 401
            .accessDeniedHandler(restAccessDeniedHandler)            // 403
        )

        // 인가 규칙
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(securityUrls.getRequestMatchers()).permitAll()
            .anyRequest().authenticated()
        )

        .build();
}
```

---

## 9. 주요 클래스 정리

### 파일 위치 맵

```
src/main/java/com/wardk/meeteam_backend/
│
├── global/
│   ├── auth/
│   │   ├── filter/
│   │   │   ├── JwtFilter.java           ← 토큰 검증 필터
│   │   │   └── LoginFilter.java         ← 일반 로그인 필터
│   │   │
│   │   ├── handler/
│   │   │   └── OAuth2AuthenticationSuccessHandler.java
│   │   │
│   │   ├── repository/
│   │   │   └── TokenBlacklistRepository.java  ← Redis 블랙리스트
│   │   │
│   │   └── service/
│   │       ├── AuthService.java         ← 회원가입/재발급/로그아웃
│   │       ├── CustomOauth2UserService.java   ← GitHub OAuth2
│   │       ├── CustomOidcUserService.java     ← Google OIDC
│   │       ├── CustomUserDetailsService.java  ← 일반 로그인 사용자 조회
│   │       └── OAuth2UserProcessor.java       ← OAuth2 공통 처리
│   │
│   ├── config/
│   │   ├── SecurityConfig.java          ← 필터 체인 설정
│   │   └── SecurityUrls.java            ← 화이트리스트 관리
│   │
│   └── util/
│       └── JwtUtil.java                 ← 토큰 생성/검증
│
└── web/
    └── auth/
        ├── controller/
        │   └── AuthController.java      ← /api/auth/* 엔드포인트
        │
        └── dto/
            ├── CustomSecurityUserDetails.java   ← 일반 로그인
            └── CustomOauth2UserDetails.java     ← OAuth2 로그인
```

### 역할 요약

| 클래스 | 역할 |
|-------|------|
| **JwtFilter** | 모든 요청에서 JWT 토큰 검증, SecurityContext 설정 |
| **LoginFilter** | POST /api/auth/login 처리, 토큰 발급 |
| **OAuth2UserProcessor** | OAuth2 사용자 정보 파싱, 기존/신규 회원 분기 |
| **OAuth2AuthenticationSuccessHandler** | OAuth2 로그인 성공 후 토큰 발급 및 리다이렉트 |
| **AuthService** | 회원가입, 토큰 재발급, 로그아웃 비즈니스 로직 |
| **JwtUtil** | JWT 토큰 생성, 검증, 클레임 추출 |
| **TokenBlacklistRepository** | Redis 기반 로그아웃 토큰 관리 |
| **SecurityConfig** | Spring Security 필터 체인 설정 |

---

## 부록: 주요 설정값

### application-local.yml

```yaml
jwt:
  secret-key: ${JWT_SECRET_KEY}
  access-exp-time: 36000000        # 10시간 (ms)
  refresh-exp-time: 604800000      # 7일 (ms)
  oauth2-signup-exp-time: 3000000  # 50분 (ms)

app:
  oauth2:
    oauth2-redirect-url: http://localhost:3000/oauth2/redirect
    providers:
      google:
        id-attribute: sub
        email-attribute: email
        user-name-attribute: name
      github:
        id-attribute: id
        email-attribute: email
        user-name-attribute: name
```

### RefreshToken 쿠키 설정

| 속성 | 값 | 설명 |
|------|-----|------|
| httpOnly | true | JavaScript 접근 차단 |
| secure | false(로컬)/true(프로덕션) | HTTPS에서만 전송 |
| domain | .meeteam.alom-sejong.com | 쿠키 적용 도메인 |
| sameSite | None | CORS 환경에서 쿠키 전송 |
| maxAge | 604800 (7일) | 쿠키 유효 기간 |
| path | / | 모든 경로에서 사용 |

---

## 문의

백엔드 관련 문의사항은 Slack #backend 채널로 연락 부탁드립니다.
