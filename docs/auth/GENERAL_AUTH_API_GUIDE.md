# 일반 회원 로그인/회원가입 API 가이드

일반 회원(이메일/비밀번호)의 **로그인**과 **회원가입** API를 정리한 문서입니다.

---

## 목차

1. [로그인 API](#1-로그인-api)
2. [회원가입 API](#2-회원가입-api)
3. [토큰 재발급 API](#3-토큰-재발급-api)
4. [로그아웃 API](#4-로그아웃-api)
5. [인증이 필요한 API 호출 방법](#5-인증이-필요한-api-호출-방법)

---

## 1. 로그인 API

### 요청

```
POST /api/auth/login
```

#### 헤더

```
Content-Type: application/json
```

#### 바디

```json
{
  "email": "user@example.com",
  "password": "qwer1234"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | O | 가입한 이메일 주소 |
| `password` | String | O | 비밀번호 |

#### fetch 코드

```jsx
const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include',   // 반드시 필요 — RefreshToken 쿠키 저장
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'qwer1234'
  }),
});
```

### 성공 응답 (HTTP 200)

백엔드에서 **3가지**가 동시에 옵니다:

#### (A) 응답 바디 — JSON

```json
{
  "code": "AUTH200",
  "message": "로그인에 성공했습니다.",
  "result": {
    "name": "홍길동",
    "memberId": 42
  }
}
```

| 경로 | 타입 | 설명 |
| --- | --- | --- |
| `code` | String | 응답 상태 코드 |
| `message` | String | 응답 메시지 |
| `result.name` | String | 로그인한 회원의 이름 |
| `result.memberId` | Long | 로그인한 회원의 ID |

#### (B) Authorization 헤더 — AccessToken

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

> **중요:** AccessToken은 응답 **헤더**에 있습니다. 바디가 아닙니다!
> `response.headers.get('Authorization')`으로 추출해야 합니다.

#### (C) Set-Cookie 헤더 — RefreshToken

```
Set-Cookie: refreshToken=eyJhbGciOi...; Path=/; Domain=.meeteam.alom-sejong.com; HttpOnly; Secure; SameSite=None
```

> RefreshToken은 HttpOnly 쿠키로 자동 저장됩니다. 직접 관리할 필요 없습니다.

### 성공 시 처리 코드

```jsx
if (response.ok) {
  const data = await response.json();

  // 1. AccessToken 추출 (헤더에서!)
  const authHeader = response.headers.get('Authorization');
  const accessToken = authHeader?.replace('Bearer ', '');

  // 2. AccessToken 저장
  if (accessToken) {
    localStorage.setItem('accessToken', accessToken);
  }

  // 3. 사용자 정보 활용
  console.log('로그인 성공:', data.result.name, data.result.memberId);

  // 4. 메인 페이지로 이동
  navigate('/');
}
```

### 실패 응답

#### 이메일 또는 비밀번호 불일치 (HTTP 401)

```json
{
  "code": "BAD_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다."
}
```

#### 이메일 미입력 (HTTP 400)

```json
{
  "code": "AUTH401",
  "message": "이메일을 입력해주세요"
}
```

#### 비밀번호 미입력 (HTTP 400)

```json
{
  "code": "AUTH402",
  "message": "비밀번호를 입력해주세요"
}
```

#### 잘못된 요청 형식 (HTTP 400)

```json
{
  "code": "COMMON400",
  "message": "잘못된 요청입니다."
}
```

### 실패 시 처리 코드

```jsx
if (!response.ok) {
  const errorData = await response.json();

  switch (errorData.code) {
    case 'BAD_CREDENTIALS':
      alert('이메일 또는 비밀번호가 올바르지 않습니다.');
      break;
    case 'AUTH401':
      alert('이메일을 입력해주세요.');
      break;
    case 'AUTH402':
      alert('비밀번호를 입력해주세요.');
      break;
    default:
      alert(errorData.message || '로그인에 실패했습니다.');
  }
  return;
}
```

### 전체 함수 (복사용)

```jsx
async function login(email, password) {
  try {
    const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
      const errorData = await response.json();
      switch (errorData.code) {
        case 'BAD_CREDENTIALS':
          alert('이메일 또는 비밀번호가 올바르지 않습니다.');
          break;
        case 'AUTH401':
          alert('이메일을 입력해주세요.');
          break;
        case 'AUTH402':
          alert('비밀번호를 입력해주세요.');
          break;
        default:
          alert(errorData.message || '로그인에 실패했습니다.');
      }
      return false;
    }

    const data = await response.json();

    // AccessToken 추출 (헤더에서!)
    const authHeader = response.headers.get('Authorization');
    const accessToken = authHeader?.replace('Bearer ', '');

    if (accessToken) {
      localStorage.setItem('accessToken', accessToken);
    }

    console.log('로그인 성공:', data.result.name);
    navigate('/');
    return true;

  } catch (error) {
    console.error('로그인 중 네트워크 오류:', error);
    alert('네트워크 오류가 발생했습니다.');
    return false;
  }
}
```

---

## 2. 회원가입 API

### 요청

```
POST /api/auth/register
```

#### 헤더

```
Content-Type: multipart/form-data    ← FormData 사용 시 브라우저가 자동 설정 (직접 설정하면 안 됨!)
```

#### 바디 — Multipart Form Data

`request`(JSON)와 `file`(이미지) 두 파트로 나뉩니다.

#### Part 1: `request` (필수)

```json
{
  "email": "user@example.com",
  "password": "qwer1234",
  "name": "홍길동",
  "birthDate": "2000-01-01",
  "gender": "MALE",
  "jobPositions": ["WEB_SERVER", "WEB_FRONTEND"],
  "projectExperienceCount": 3,
  "skills": ["Java", "Spring Boot", "MySQL"],
  "githubUrl": "https://github.com/username",
  "blogUrl": "https://blog.example.com"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | O | 이메일 주소 (로그인 ID로 사용) |
| `password` | String | O | 비밀번호 (최소 8자 이상) |
| `name` | String | O | 이름 |
| `birthDate` | String | O | 생년월일 (`YYYY-MM-DD` 형식) |
| `gender` | String | O | `"MALE"` 또는 `"FEMALE"` |
| `jobPositions` | String[] | O | 관심 분야 (JobPosition enum 값 배열) |
| `projectExperienceCount` | Integer | O | 프로젝트 경험 횟수 |
| `skills` | String[] | X | 기술스택 이름 배열 (선택) |
| `githubUrl` | String | X | GitHub URL (선택) |
| `blogUrl` | String | X | 블로그 URL (선택) |

#### jobPositions 가능한 값

```
WEB_FRONTEND, IOS, ANDROID, CROSS_PLATFORM, WEB_SERVER, AI,
PRODUCT_MANAGER, GRAPHIC_DESIGN, UI_UX_DESIGN, MOTION_DESIGN, ETC
```

#### Part 2: `file` (선택)

프로필 이미지 파일입니다. 없으면 안 보내도 됩니다.

#### fetch 코드

```jsx
// 1. JSON request 데이터 구성
const requestData = {
  email: 'user@example.com',
  password: 'qwer1234',
  name: '홍길동',
  birthDate: '2000-01-01',
  gender: 'MALE',
  jobPositions: ['WEB_SERVER', 'WEB_FRONTEND'],
  projectExperienceCount: 3,
  skills: ['Java', 'Spring Boot'],      // 선택
  githubUrl: 'https://github.com/user', // 선택
  blogUrl: 'https://blog.example.com'   // 선택
};

// 2. FormData 구성
const formData = new FormData();

// request part: JSON을 Blob으로 감싸서 넣어야 합니다
formData.append(
  'request',
  new Blob([JSON.stringify(requestData)], { type: 'application/json' })
);

// file part: 프로필 이미지 (선택)
if (profileImageFile) {
  formData.append('file', profileImageFile);
}

// 3. 요청
const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/register', {
  method: 'POST',
  body: formData,
  // Content-Type 헤더를 직접 설정하면 안 됩니다!
});
```

> **주의:** `headers: { 'Content-Type': 'multipart/form-data' }`를 직접 설정하면 **요청이 실패합니다.**

### 성공 응답 (HTTP 200)

```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "username": "홍길동",
    "memberId": 42
  }
}
```

| 경로 | 타입 | 설명 |
| --- | --- | --- |
| `result.username` | String | 가입한 회원의 이름 |
| `result.memberId` | Long | 가입한 회원의 ID |

> **참고:** 회원가입 성공 후에는 **자동 로그인되지 않습니다.**
> 가입 완료 후 로그인 페이지로 이동하여 로그인해야 합니다.

### 성공 시 처리 코드

```jsx
if (response.ok) {
  const data = await response.json();
  console.log('회원가입 성공:', data.result.username);

  alert('회원가입이 완료되었습니다. 로그인해주세요.');
  navigate('/login');
}
```

### 실패 응답

#### 이메일 중복 (HTTP 400)

```json
{
  "code": "MEMBER400",
  "message": "이미 존재하는 회원입니다."
}
```

#### 이메일 형식 오류 (HTTP 400)

```json
{
  "code": "COMMON400",
  "message": "올바른 이메일 형식이 아닙니다"
}
```

#### 비밀번호 길이 부족 (HTTP 400)

```json
{
  "code": "COMMON400",
  "message": "비밀번호는 최소 8자 이상이어야 합니다"
}
```

#### 필수 필드 누락 (HTTP 400)

```json
{
  "code": "COMMON400",
  "message": "필수 항목을 모두 입력해주세요"
}
```

#### 관심분야 미선택 (HTTP 400)

```json
{
  "code": "COMMON400",
  "message": "관심분야는 최소 1개 이상 선택해야 합니다"
}
```

### 실패 시 처리 코드

```jsx
if (!response.ok) {
  const errorData = await response.json();

  if (errorData.code === 'MEMBER400') {
    alert('이미 가입된 이메일입니다.');
  } else {
    alert(errorData.message || '회원가입에 실패했습니다.');
  }
  return;
}
```

### 전체 함수 (복사용)

```jsx
async function register({ email, password, name, birthDate, gender, jobPositions, projectExperienceCount, skills, githubUrl, blogUrl, profileImage }) {

  const requestData = {
    email,
    password,
    name,
    birthDate,
    gender,
    jobPositions,
    projectExperienceCount,
    skills,       // 선택
    githubUrl,    // 선택
    blogUrl       // 선택
  };

  const formData = new FormData();
  formData.append(
    'request',
    new Blob([JSON.stringify(requestData)], { type: 'application/json' })
  );

  if (profileImage) {
    formData.append('file', profileImage);
  }

  try {
    const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/register', {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const errorData = await response.json();
      if (errorData.code === 'MEMBER400') {
        alert('이미 가입된 이메일입니다.');
      } else {
        alert(errorData.message || '회원가입에 실패했습니다.');
      }
      return false;
    }

    const data = await response.json();
    console.log('회원가입 성공:', data.result.username);

    alert('회원가입이 완료되었습니다. 로그인해주세요.');
    navigate('/login');
    return true;

  } catch (error) {
    console.error('회원가입 중 네트워크 오류:', error);
    alert('네트워크 오류가 발생했습니다.');
    return false;
  }
}
```

---

## 3. 토큰 재발급 API

AccessToken이 만료되었을 때 RefreshToken(쿠키)을 사용하여 새 AccessToken을 발급받습니다.

### 요청

```
POST /api/auth/refresh
```

#### 헤더

```
Content-Type: application/json
```

> **중요:** RefreshToken은 쿠키에 자동으로 포함됩니다.
> `credentials: 'include'`만 설정하면 됩니다.

#### fetch 코드

```jsx
const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/refresh', {
  method: 'POST',
  credentials: 'include',   // 반드시 필요 — 쿠키의 RefreshToken 전송
});
```

### 성공 응답 (HTTP 200)

```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": "eyJhbGciOiJIUzI1NiJ9..."
}
```

| 경로 | 타입 | 설명 |
| --- | --- | --- |
| `result` | String | 새로 발급된 AccessToken |

### 성공 시 처리 코드

```jsx
const data = await response.json();
const newAccessToken = data.result;

// 새 토큰 저장
localStorage.setItem('accessToken', newAccessToken);
```

### 실패 응답

#### RefreshToken 없음 (HTTP 400)

```json
{
  "code": "AUTH403",
  "message": "Refresh Token이 존재하지 않습니다."
}
```

#### RefreshToken 만료 (HTTP 401)

```json
{
  "code": "AUTH404",
  "message": "Refresh Token이 만료되었습니다."
}
```

> RefreshToken이 만료되면 **다시 로그인**해야 합니다.

---

## 4. 로그아웃 API

### 요청

```
POST /api/auth/logout
```

#### 헤더

```
Authorization: Bearer {accessToken}
```

#### fetch 코드

```jsx
const accessToken = localStorage.getItem('accessToken');

const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/logout', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
  },
  credentials: 'include',   // RefreshToken 쿠키 삭제를 위해 필요
});
```

### 성공 응답 (HTTP 200)

```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": "로그아웃이 완료되었습니다."
}
```

> 백엔드에서:
> - AccessToken을 블랙리스트에 등록 (재사용 방지)
> - RefreshToken 쿠키 삭제

### 성공 시 처리 코드

```jsx
// 클라이언트에서도 토큰 삭제
localStorage.removeItem('accessToken');

// 로그인 페이지로 이동
navigate('/login');
```

### 전체 함수 (복사용)

```jsx
async function logout() {
  const accessToken = localStorage.getItem('accessToken');

  try {
    await fetch('https://api.meeteam.alom-sejong.com/api/auth/logout', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
      },
      credentials: 'include',
    });
  } catch (error) {
    console.error('로그아웃 요청 실패:', error);
  }

  // 성공/실패 관계없이 클라이언트 토큰 삭제
  localStorage.removeItem('accessToken');
  navigate('/login');
}
```

---

## 5. 인증이 필요한 API 호출 방법

로그인 후 인증이 필요한 API를 호출할 때는 **Authorization 헤더**에 AccessToken을 포함해야 합니다.

### 기본 패턴

```jsx
const accessToken = localStorage.getItem('accessToken');

const response = await fetch('https://api.meeteam.alom-sejong.com/api/some-endpoint', {
  method: 'GET',  // 또는 POST, PUT, DELETE 등
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json',  // POST/PUT일 때
  },
  credentials: 'include',
});
```

### Axios Interceptor 설정 (권장)

매번 헤더를 설정하지 않으려면 Interceptor를 사용하세요.

```jsx
import axios from 'axios';

const api = axios.create({
  baseURL: 'https://api.meeteam.alom-sejong.com',
  withCredentials: true,  // credentials: 'include'와 동일
});

// Request Interceptor: 모든 요청에 AccessToken 자동 추가
api.interceptors.request.use((config) => {
  const accessToken = localStorage.getItem('accessToken');
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// Response Interceptor: 401 에러 시 토큰 재발급 자동 처리
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // 401 에러이고, 아직 재시도 안 했으면
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // 토큰 재발급 요청
        const refreshResponse = await axios.post(
          'https://api.meeteam.alom-sejong.com/api/auth/refresh',
          {},
          { withCredentials: true }
        );

        const newAccessToken = refreshResponse.data.result;
        localStorage.setItem('accessToken', newAccessToken);

        // 새 토큰으로 원래 요청 재시도
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);

      } catch (refreshError) {
        // 재발급도 실패하면 로그아웃
        localStorage.removeItem('accessToken');
        alert('로그인이 만료되었습니다.');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

### 사용 예시

```jsx
import api from './api';

// GET 요청
const getProjects = async () => {
  const response = await api.get('/api/projects');
  return response.data;
};

// POST 요청
const createProject = async (projectData) => {
  const response = await api.post('/api/projects', projectData);
  return response.data;
};
```

---

## 참고: 에러 코드 총정리

| code | HTTP 상태 | message | 의미 |
| --- | --- | --- | --- |
| `BAD_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 실패 |
| `AUTH401` | 400 | 이메일을 입력해주세요 | 이메일 미입력 |
| `AUTH402` | 400 | 비밀번호를 입력해주세요 | 비밀번호 미입력 |
| `AUTH403` | 400 | Refresh Token이 존재하지 않습니다. | 쿠키에 RefreshToken 없음 |
| `AUTH404` | 401 | Refresh Token이 만료되었습니다. | RefreshToken 만료 |
| `MEMBER400` | 400 | 이미 존재하는 회원입니다. | 이메일 중복 |
| `COMMON400` | 400 | (validation 메시지) | 입력값 검증 실패 |

---

## 주의사항 체크리스트

### 로그인 시
- [ ] `credentials: 'include'` 설정했는지 확인
- [ ] AccessToken은 **응답 헤더**에서 추출 (`response.headers.get('Authorization')`)
- [ ] RefreshToken은 쿠키로 자동 저장됨 (직접 처리 불필요)

### 회원가입 시
- [ ] `Content-Type` 헤더를 **직접 설정하지 않음** (FormData 사용 시)
- [ ] JSON 데이터는 `Blob`으로 감싸서 FormData에 추가
- [ ] 비밀번호는 최소 8자 이상
- [ ] `jobPositions`는 최소 1개 이상 선택

### API 호출 시
- [ ] Authorization 헤더에 `Bearer {accessToken}` 형식으로 전달
- [ ] 401 에러 시 토큰 재발급 → 원래 요청 재시도 로직 구현
- [ ] 재발급도 실패하면 로그아웃 처리