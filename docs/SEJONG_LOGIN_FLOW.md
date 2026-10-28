# 세종대 포털 로그인 흐름

> 기준: 공개 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`. 배포 환경이나 미커밋 테스트 파일이 아닌 해당 커밋의 소스 계약을 설명합니다.
> 인증·쿠키 처리는 [AuthController](../src/main/java/com/wardk/meeteam_backend/web/auth/controller/AuthController.java), 프로필은 [MemberController](../src/main/java/com/wardk/meeteam_backend/web/member/controller/MemberController.java)를 참고하세요.

## 개요

세종대학교 포털 계정을 통한 로그인/회원가입 흐름입니다.
신규 회원은 Redis 일회용 코드로 온보딩을 처리합니다.
일반 계정은 매 로그인마다 실제 포털 인증을 거칩니다. 명시적으로 활성화한 비운영 테스트 계정만 예외입니다.

---

## 전체 흐름도

```
┌─────────────────────────────────────────────────────────────────┐
│                        클라이언트                                │
└─────────────────────────────────────────────────────────────────┘
        │
        │ 1. POST /api/v1/auth/login/sejong
        │    { studentId, password }
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                        MeeTeam 서버                              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 세종대 포털 인증 (portal.sejong.ac.kr)                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│        │                                                        │
│        ├── 인증 실패 → 원인별 400/401/503                            │
│        │                                                        │
│        └── 인증 성공 → DB에서 학번으로 회원 조회                 │
│                │                                                │
│                ├── 기존 회원 → 토큰 발급 (쿠키)                  │
│                │              { newMember: false }            │
│                │                                                │
│                └── 신규 회원 → 임시 코드 발급 (Redis, 10분 유효) │
│                               { newMember: true, code: "..." }│
└─────────────────────────────────────────────────────────────────┘
        │
        │ (신규 회원인 경우)
        │
        │ 2. POST /api/v1/auth/register/sejong
        │    { code, name, birthDate, gender, jobPositions, ... }
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                        MeeTeam 서버                              │
│  - 코드로 학번 조회 (Redis)                                      │
│  - 회원 생성                                                     │
│  - 토큰 발급 (쿠키)                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## API 상세

### 1. 세종대 포털 로그인

세종대 포털 계정으로 인증을 시도합니다.

**Endpoint**
```
POST /api/v1/auth/login/sejong
Content-Type: application/json
```

**Request Body**
```json
{
  "studentId": "<학번>",
  "password": "<포털 비밀번호>"
}
```

**Response - 기존 회원**
```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "newMember": false,
    "code": null
  }
}
```
- `accessToken`, `refreshToken`을 HttpOnly 쿠키로 설정합니다. Path는 `/`, Secure/SameSite/Domain은 `app.cookie.*` 설정을 따릅니다. 기본값은 Secure=true, SameSite=None이며 Domain은 설정된 경우에만 붙습니다.

**Response - 신규 회원**
```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "newMember": true,
    "code": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```
- 쿠키 없음 (토큰 미발급)
- `code`는 10분간 유효

**Error Response** (아래 블록은 원인별 개별 응답 예시입니다.)
```jsonc
// 학번 또는 비밀번호 불일치
{
  "code": "SEJONG401",
  "message": "학번 또는 비밀번호가 일치하지 않습니다."
}

// 비밀번호 재설정 필요
{
  "code": "SEJONG402",
  "message": "비밀번호 재설정이 필요합니다. 세종대 포털에서 비밀번호를 변경해주세요."
}

// 포털 서버 연결 실패
{
  "code": "SEJONG503",
  "message": "세종대 포털 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
}
```

---

### 2. 세종대 회원가입 (온보딩)

신규 회원이 온보딩 정보를 입력하여 회원가입을 완료합니다.

**Endpoint**
```
POST /api/v1/auth/register/sejong
Content-Type: multipart/form-data
```

**Request**
```
request (JSON):
{
  "code": "550e8400-e29b-41d4-a716-446655440000",
  "name": "홍길동",
  "birthDate": "2000-01-01",
  "gender": "MALE",
  "jobPositions": [
    {
      "jobFieldCode": "BACKEND",
      "jobPositionCode": "JAVA_SPRING",
      "techStacks": [
        { "id": 1, "displayOrder": 1 },
        { "id": 2, "displayOrder": 2 }
      ]
    }
  ],
  "githubUrl": "https://github.com/username",
  "blogUrl": "https://blog.example.com"
}

file (optional): 프로필 이미지 파일
```

**Response - 성공**
```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다."
}
```
- `accessToken`, `refreshToken`을 HttpOnly 쿠키로 설정합니다. Path는 `/`, Secure/SameSite/Domain은 `app.cookie.*` 설정을 따릅니다. 기본값은 Secure=true, SameSite=None이며 Domain은 설정된 경우에만 붙습니다.

**Error Response: 유효하지 않은 코드 (만료 또는 이미 사용됨, HTTP 400)**
```json
{
  "code": "SEJONG404",
  "message": "유효하지 않거나 만료된 세종대 인증 코드입니다."
}
```

**Error Response: 이미 가입된 학번 (HTTP 400)**
```json
{
  "code": "SEJONG400",
  "message": "이미 등록된 학번입니다."
}
```

---

## 클라이언트 구현 가이드

### 1. 로그인 페이지

```typescript
// 로그인 요청
async function sejongLogin(studentId: string, password: string) {
  const response = await fetch('/api/v1/auth/login/sejong', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include', // cross-origin 쿠키 송수신 허용
    body: JSON.stringify({ studentId, password })
  });

  const result = await response.json();

  if (!response.ok) {
    // 에러 처리
    alert(result.message);
    return;
  }

  if (result.result.newMember) {
    // 신규 회원 → 온보딩 페이지로 이동
    // 코드를 state나 sessionStorage에 저장
    navigate('/onboarding', {
      state: { code: result.result.code }
    });
  } else {
    // 기존 회원 → 메인 페이지로 이동
    // 쿠키에 토큰이 자동으로 설정됨
    navigate('/');
  }
}
```

### 2. 온보딩 페이지

```typescript
// 회원가입 요청
async function sejongRegister(code: string, jobPositions: object[], profileImage?: File) {
  // request는 application/json 파트, file은 선택 파일 파트
  const requestData = {
    code,
    name: '홍길동',
    birthDate: '2000-01-01',
    gender: 'MALE',
    jobPositions,
    githubUrl: 'https://github.com/username',
    blogUrl: null
  };

  const formData = new FormData();
  formData.append('request', new Blob([JSON.stringify(requestData)], {
    type: 'application/json'
  }));

  // 프로필 이미지가 있는 경우
  if (profileImage) {
    formData.append('file', profileImage);
  }

  const response = await fetch('/api/v1/auth/register/sejong', {
    method: 'POST',
    credentials: 'include',
    body: formData
  });

  const result = await response.json();

  if (!response.ok) {
    alert(result.message);
    return;
  }

  // 회원가입 완료 → 메인 페이지로 이동
  navigate('/');
}
```

### 3. 주의사항

1. **코드 유효 시간**: 로그인 후 발급받은 `code`는 **10분간 유효**합니다. 온보딩 페이지에서 시간이 초과되면 다시 로그인해야 합니다.

2. **쿠키 설정**: cross-origin 쿠키 요청에는 `credentials: 'include'`를 포함하세요. 서버 CORS 허용 origin/credentials와 브라우저의 쿠키 정책도 충족해야 합니다. SameSite=None은 Secure/HTTPS가 필요하며 credentials만으로 브라우저 차단을 해제하지 못합니다.

3. **코드 보관**: 코드는 회원가입 검증/저장 전에 원자적으로 소비됩니다. 코드 소비 이후 회원가입이 실패하면 재로그인하여 새 코드를 받으세요.

4. **코드 보관 위치**: 신규 회원의 경우 `code`를 온보딩 완료 전까지 보관해야 합니다. `sessionStorage` 또는 React state 사용을 권장합니다.

5. **에러 처리**:
   - `SEJONG401`: 학번/비밀번호 오류 → 재입력 요청
   - `SEJONG402`: 비밀번호 재설정 필요 → 세종대 포털로 안내
   - `SEJONG404`: 코드 없음/만료/사용됨 → 로그인 페이지로 리다이렉트

---

## 시퀀스 다이어그램

아래 경로는 `/api/v1/auth` 접두사를 생략했고, 응답은 result 내부를 요약했습니다.

```
Client                    MeeTeam Server              Sejong Portal           Redis
   │                            │                          │                    │
   │ 1. POST /login/sejong      │                          │                    │
   │ ─────────────────────────> │                          │                    │
   │                            │ 2. 포털 인증 요청         │                    │
   │                            │ ────────────────────────>│                    │
   │                            │ 3. 인증 결과 (OK/FAIL)   │                    │
   │                            │ <────────────────────────│                    │
   │                            │                          │                    │
   │                            │ 4. DB 회원 조회          │                    │
   │                            │                          │                    │
   │                            │ [신규 회원인 경우]        │                    │
   │                            │ 5. 학번 저장              │                    │
   │                            │ ────────────────────────────────────────────> │
   │                            │ 6. code 반환             │                    │
   │                            │ <──────────────────────────────────────────── │
   │                            │                          │                    │
   │ 7. { newMember: true,    │                          │                    │
   │      code: "..." }         │                          │                    │
   │ <───────────────────────── │                          │                    │
   │                            │                          │                    │
   │ 8. POST /register/sejong   │                          │                    │
   │ ─────────────────────────> │                          │                    │
   │                            │ 9. 코드로 학번 조회       │                    │
   │                            │ ────────────────────────────────────────────> │
   │                            │ 10. studentId 반환       │                    │
   │                            │ <──────────────────────────────────────────── │
   │                            │                          │                    │
   │                            │ 11. 회원 생성 & 토큰 발급 │                    │
   │                            │                          │                    │
   │ 12. 200 OK + Set-Cookie    │                          │                    │
   │ <───────────────────────── │                          │                    │
   │                            │                          │                    │
```

## 응답 및 검증 주의사항

- 성공 응답은 `code`, `message`, 선택적 `result`입니다. `isSuccess`/`data` 필드는 없습니다. null인 최상위 `result`는 생략됩니다.
- DTO의 Java 필드는 `isNewMember`이지만 Lombok boolean getter `isNewMember()`에 대한 기본 Jackson 속성명은 `newMember`입니다. 위 예시는 이 기본 직렬화 기준이며 실행 검증은 하지 않았습니다.
- 로그인은 학번/비밀번호 모두 NotBlank입니다. 회원가입은 code/name/birthDate/gender와 최소 1개 jobPositions가 필수입니다. githubUrl/blogUrl과 이미지, 기술스택은 선택입니다. projectExperienceCount는 현재 회원가입 DTO에 없습니다.
- 직군/직무는 ENUM 코드, 기술스택은 DB ID와 1 이상 displayOrder를 사용합니다. 실제 옵션은 `GET /api/v1/jobs/options`로 확인하세요. 예시 ID는 배포 DB 존재를 보장하지 않습니다.
- 포털 로그인 실패 SEJONG401은 HTTP 401, 비밀번호 재설정 SEJONG402는 HTTP 400, 포털 오류 SEJONG503은 HTTP 503입니다. 코드 오류 SEJONG404와 학번 중복 SEJONG400은 HTTP 400입니다.

## 소스 근거 (위 기준 커밋의 줄 번호)

경로 기준: `src/main/java/com/wardk/meeteam_backend/`.

- `web/auth/controller/AuthController.java:35-85`: 로그인/멀티파트 회원가입 매핑, 분기, 쿠키 설정.
- `global/auth/service/AuthService.java:90-158`: 테스트 계정 예외, 실제 포털 인증, 활성 회원, 코드 소비 및 회원가입.
- `global/auth/repository/SejongCodeRepository.java:27-41,56-69`: 10분 TTL, getAndDelete.
- `web/auth/dto/sejong/SejongLoginResponse.java:6-18`: boolean getter와 로그인 응답.
- `web/auth/dto/sejong/SejongRegisterRequest.java:20-47`: 현재 온보딩 필드/필수 조건.
- `web/auth/dto/register/MemberJobPositionRequest.java:17-36`, `TechStackOrderRequest.java:11-19`: 직무/스택 검증.
- `global/response/SuccessResponse.java:10-21`, `SuccessCode.java:10`, `ErrorResponse.java:10-19`, `ErrorCode.java:31-35`: 실제 응답 구조/코드/HTTP 상태.
- `global/auth/cookie/AccessTokenCookieProvider.java:30-42`, `RefreshTokenCookieProvider.java:30-42`, `global/config/CookieProperties.java:32-46`: 쿠키 속성.
