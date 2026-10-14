# 세종대 포털 로그인 흐름

## 개요

세종대학교 포털 계정을 통한 로그인/회원가입 흐름입니다.
OAuth2 방식과 유사하게 임시 코드를 발급하여 신규 회원의 온보딩을 처리합니다.

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
│        ├── 인증 실패 → 401 에러 반환                            │
│        │                                                        │
│        └── 인증 성공 → DB에서 학번으로 회원 조회                 │
│                │                                                │
│                ├── 기존 회원 → 토큰 발급 (쿠키)                  │
│                │              { isNewMember: false }            │
│                │                                                │
│                └── 신규 회원 → 임시 코드 발급 (Redis, 10분 유효) │
│                               { isNewMember: true, code: "..." }│
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
  "studentId": "21013220",
  "password": "비밀번호"
}
```

**Response - 기존 회원**
```json
{
  "isSuccess": true,
  "code": "_OK",
  "message": "OK",
  "data": {
    "isNewMember": false,
    "code": null
  }
}
```
- `Set-Cookie: accessToken=...; HttpOnly; Secure; SameSite=Strict`
- `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict`

**Response - 신규 회원**
```json
{
  "isSuccess": true,
  "code": "_OK",
  "message": "OK",
  "data": {
    "isNewMember": true,
    "code": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```
- 쿠키 없음 (토큰 미발급)
- `code`는 10분간 유효

**Error Response**
```json
// 학번 또는 비밀번호 불일치
{
  "isSuccess": false,
  "code": "SEJONG401",
  "message": "학번 또는 비밀번호가 일치하지 않습니다."
}

// 비밀번호 재설정 필요
{
  "isSuccess": false,
  "code": "SEJONG402",
  "message": "비밀번호 재설정이 필요합니다."
}

// 포털 서버 연결 실패
{
  "isSuccess": false,
  "code": "SEJONG503",
  "message": "세종대 포털 서버에 연결할 수 없습니다."
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
      "jobPositionCode": "SPRING_DEVELOPER",
      "techStacks": [
        { "id": 1, "displayOrder": 1 },
        { "id": 2, "displayOrder": 2 }
      ]
    }
  ],
  "projectExperienceCount": 3,
  "githubUrl": "https://github.com/username",
  "blogUrl": "https://blog.example.com"
}

file (optional): 프로필 이미지 파일
```

**Response - 성공**
```json
{
  "isSuccess": true,
  "code": "_OK",
  "message": "OK",
  "data": null
}
```
- `Set-Cookie: accessToken=...; HttpOnly; Secure; SameSite=Strict`
- `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict`

**Error Response**
```json
// 유효하지 않은 코드 (만료 또는 이미 사용됨)
{
  "isSuccess": false,
  "code": "AUTH400",
  "message": "유효하지 않은 인증 코드입니다."
}

// 이미 가입된 학번
{
  "isSuccess": false,
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
    credentials: 'include', // 쿠키 수신을 위해 필수
    body: JSON.stringify({ studentId, password })
  });

  const result = await response.json();

  if (!result.isSuccess) {
    // 에러 처리
    alert(result.message);
    return;
  }

  if (result.data.isNewMember) {
    // 신규 회원 → 온보딩 페이지로 이동
    // 코드를 state나 sessionStorage에 저장
    navigate('/onboarding', {
      state: { code: result.data.code }
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
async function sejongRegister(code: string, formData: FormData) {
  // formData에는 'request' (JSON) 와 'file' (이미지)가 포함됨
  const requestData = {
    code,
    name: '홍길동',
    birthDate: '2000-01-01',
    gender: 'MALE',
    jobPositions: [...],
    projectExperienceCount: 3,
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

  if (!result.isSuccess) {
    alert(result.message);
    return;
  }

  // 회원가입 완료 → 메인 페이지로 이동
  navigate('/');
}
```

### 3. 주의사항

1. **코드 유효 시간**: 로그인 후 발급받은 `code`는 **10분간 유효**합니다. 온보딩 페이지에서 시간이 초과되면 다시 로그인해야 합니다.

2. **쿠키 설정**: 모든 API 요청에 `credentials: 'include'`를 포함해야 쿠키가 정상적으로 전송/수신됩니다.

3. **코드 보관**: 신규 회원의 경우 `code`를 온보딩 완료 전까지 보관해야 합니다. `sessionStorage` 또는 React state 사용을 권장합니다.

4. **에러 처리**:
   - `SEJONG401`: 학번/비밀번호 오류 → 재입력 요청
   - `SEJONG402`: 비밀번호 재설정 필요 → 세종대 포털로 안내
   - `AUTH400`: 코드 만료 → 로그인 페이지로 리다이렉트

---

## 시퀀스 다이어그램

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
   │ 7. { isNewMember: true,    │                          │                    │
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