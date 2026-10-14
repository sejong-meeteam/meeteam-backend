# OAuth API 요청/응답 상세 가이드

소셜 로그인 과정의 **전체 흐름(Timeline)**과, 리다이렉트 이후 프론트엔드가 백엔드로 보내야 하는 **정확한 API 요청/응답**을 정리한 문서입니다.

---

## 0. 전체 흐름 (Timeline)

이 문서는 **Step 4 이후**의 과정을 상세히 다룹니다.

### Step 1: 소셜 로그인 버튼 클릭 (프론트)

- 사용자가 "구글로 로그인" 또는 "깃허브로 로그인" 버튼을 클릭합니다.
- 프론트는 백엔드의 소셜 로그인 주소로 이동합니다 (예: `a href` 또는 `window.location.href`).

| Provider | URL |
|----------|-----|
| Google | `https://api.meeteam.alom-sejong.com/oauth2/authorization/google` |
| GitHub | `https://api.meeteam.alom-sejong.com/oauth2/authorization/github` |

#### 구현 예시

```jsx
// React 예시
<a href="https://api.meeteam.alom-sejong.com/oauth2/authorization/google">
  구글로 로그인
</a>

<a href="https://api.meeteam.alom-sejong.com/oauth2/authorization/github">
  깃허브로 로그인
</a>
```

```jsx
// 또는 버튼 클릭 시
const handleGoogleLogin = () => {
  window.location.href = 'https://api.meeteam.alom-sejong.com/oauth2/authorization/google';
};

const handleGithubLogin = () => {
  window.location.href = 'https://api.meeteam.alom-sejong.com/oauth2/authorization/github';
};
```

### Step 2: 소셜 인증 및 동의 (사용자 ↔ 구글)

- 사용자가 구글 로그인 창에서 로그인을 완료하고 정보 제공에 동의합니다.

### Step 3: 백엔드 처리 (구글 → 백엔드)

- 구글이 백엔드 서버로 리다이렉트합니다 (Callback).
- 백엔드는 구글에서 사용자 이메일 등을 받아 DB를 조회합니다.
- **기존 회원인지 신규 회원인지 판단**하고, 보안을 위한 일회용 `code`를 생성합니다.

### Step 4: 프론트로 리다이렉트 (백엔드 → 프론트)

- 백엔드가 프론트엔드의 리다이렉트 페이지로 다시 이동시킵니다.
- 이때 URL 뒤에 `code`와 `type`을 붙여서 보냅니다.
- **예시:** [`https://meeteam.alom-sejong.com/oauth2/redirect?code=UUID...&type=login`](https://meeteam.alom-sejong.com/oauth2/redirect?code=UUID...&type=login)

### Step 5: API 호출 (프론트 → 백엔드)

- 프론트엔드 페이지가 로드되면서 URL에 있는 `code`와 `type`을 확인합니다.
- `type`에 따라 아래 두 가지 중 하나의 행동을 취합니다.
    1. **`type=login` (기존 회원)**: 즉시 **[2. 토큰 교환 API]**를 호출하여 로그인합니다.
    2. **`type=register` (신규 회원)**: 회원가입 폼을 보여주고, 입력이 완료되면 **[3. OAuth 회원가입 API]**를 호출합니다.

---

## 목차

1. [리다이렉트 URL 파싱](#1-리다이렉트-url-파싱)
2. [기존 회원 — 토큰 교환 API](#2-기존-회원--토큰-교환-api)
3. [신규 회원 — OAuth 회원가입 API](#3-신규-회원--oauth-회원가입-api)

---

## 1. 리다이렉트 URL 파싱

소셜 로그인이 완료되면 백엔드가 프론트 URL로 리다이렉트합니다.

### 받게 되는 URL 예시

```
https://meeteam.alom-sejong.com/oauth2/redirect?code=550e8400-e29b-41d4-a716-446655440000&type=login
```

```
https://meeteam.alom-sejong.com/oauth2/redirect?code=7c9e2f3a-1b4d-4e8f-a5c6-3d7e9f0a1b2c&type=register
```

### 파싱해야 하는 파라미터

| 파라미터 | 값 | 의미 |
| --- | --- | --- |
| `code` | UUID 문자열 (예: `550e8400-e29b-41d4-a716-446655440000`) | 백엔드에 보내야 하는 일회용 인증 코드 |
| `type` | `"login"` 또는 `"register"` | 기존 회원이면 `login`, 신규 회원이면 `register` |

### 파싱 코드

```jsx
const params = new URLSearchParams(window.location.search);
const code = params.get('code');   // "550e8400-e29b-41d4-a716-446655440000"
const type = params.get('type');   // "login" 또는 "register"

// 파싱 후 URL에서 파라미터 제거 (보안)
window.history.replaceState({}, document.title, window.location.pathname);
```

### 분기 처리

```jsx
if (type === 'login') {
  // → 2번 섹션: 토큰 교환 API 호출
  await exchangeToken(code);
} else if (type === 'register') {
  // → 3번 섹션: code를 저장하고 회원가입 폼으로 이동
  sessionStorage.setItem('oauthCode', code);
  navigate('/signup/oauth');  // React Router 등
}
```

---

## 2. 기존 회원 — 토큰 교환 API

`type=login`일 때 호출합니다.

### 요청

```
POST /api/auth/token/exchange
```

#### 헤더

```
Content-Type: application/json
```

> Authorization 헤더는 **필요 없습니다.** 이 시점에는 아직 토큰이 없으니까요.

#### 바디

```json
{
  "code": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `code` | String | O | URL에서 파싱한 `code` 값을 그대로 넣으세요 |

#### fetch 코드

```jsx
const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/token/exchange', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include',   // 반드시 필요 — 이게 없으면 쿠키가 저장 안 됨
  body: JSON.stringify({
    code: code               // URL에서 파싱한 code
  }),
});
```

### 성공 응답 (HTTP 200)

백엔드에서 **2가지**가 동시에 옵니다:

#### (A) 응답 바디 — JSON

```json
{
  "code": "TOKEN200",
  "message": "토큰 교환에 성공했습니다.",
  "result": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImY0ZjVjNjRkLTBiMzMtNDA5MS..."
  }
}
```

| 경로 | 타입 | 설명 |
| --- | --- | --- |
| `code` | String | 응답 상태 코드 (`"TOKEN200"`) |
| `message` | String | 응답 메시지 |
| `result.accessToken` | String | **이후 모든 API 호출에 사용할 JWT 토큰** |

#### (B) Set-Cookie 헤더 — 브라우저가 자동 처리

```
Set-Cookie: refreshToken=eyJhbGciOi...; Path=/; Domain=.meeteam.alom-sejong.com; HttpOnly; Secure; SameSite=None
```

> 이건 프론트에서 직접 읽거나 저장할 필요가 **없습니다.**
> `credentials: 'include'`만 설정했으면 브라우저가 알아서 쿠키에 저장합니다.

### 성공 시 처리 코드

```jsx
const data = await response.json();

// 1. accessToken을 저장
localStorage.setItem('accessToken', data.result.accessToken);

// 2. 메인 페이지로 이동
navigate('/');
```

### 실패 응답

#### 코드 만료 또는 이미 사용됨 (HTTP 400)

```json
{
  "code": "OAUTH404",
  "message": "유효하지 않거나 만료된 OAuth 인증 코드입니다."
}
```

**언제 발생하나요?**

- 로그인 코드는 **60초** 후에 만료됩니다
- 코드를 이미 한 번 사용한 경우 (새로고침 등으로 중복 요청)
- code 값이 잘못된 경우

#### code 필드가 비어있을 때 (HTTP 400)

```json
{
  "code": "COMMON400",
  "message": "입력값이 올바르지 않습니다."
}
```

### 실패 시 처리 코드

```jsx
if (!response.ok) {
  const errorData = await response.json();
  if (errorData.code === 'OAUTH404') {
    // 코드 만료 → 소셜 로그인을 처음부터 다시
    alert('인증이 만료되었습니다. 다시 로그인해주세요.');
    navigate('/login');
    return;
  }
  // 기타 에러
  alert(errorData.message);
  navigate('/login');
  return;
}
```

### 전체 함수 (복사용)

```jsx
async function exchangeToken(code) {
  try {
    const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/token/exchange', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      body: JSON.stringify({ code }),
    });

    if (!response.ok) {
      const errorData = await response.json();
      if (errorData.code === 'OAUTH404') {
        alert('인증이 만료되었습니다. 다시 로그인해주세요.');
      } else {
        alert(errorData.message || '로그인에 실패했습니다.');
      }
      navigate('/login');
      return;
    }

    const data = await response.json();

    // accessToken 저장
    localStorage.setItem('accessToken', data.result.accessToken);

    // 메인 페이지로 이동
    navigate('/');
  } catch (error) {
    console.error('토큰 교환 중 네트워크 오류:', error);
    alert('네트워크 오류가 발생했습니다.');
    navigate('/login');
  }
}
```

---

## 3. 신규 회원 — OAuth 회원가입 API

`type=register`일 때 사용합니다.

리다이렉트 콜백에서 바로 호출하는 게 아니라, **회원가입 폼에서 사용자 입력을 받은 후** 호출합니다.

### 사전 준비: code 저장

리다이렉트 콜백 페이지에서 code를 저장해둡니다.

```jsx
// 리다이렉트 콜백 페이지에서
sessionStorage.setItem('oauthCode', code);
navigate('/signup/oauth');
```

### 요청

```
POST /api/auth/register/oauth2
```

#### 헤더

```
Content-Type: multipart/form-data    ← FormData 사용 시 브라우저가 자동 설정 (직접 설정하면 안 됨!)
```

> Authorization 헤더는 **필요 없습니다.**

#### 바디 — Multipart Form Data

이 API는 JSON이 아니라 **Multipart Form Data**로 보내야 합니다.

`request`(JSON)와 `file`(이미지) 두 파트로 나뉩니다.

#### Part 1: `request` (필수)

JSON 형식의 가입 정보입니다.

```json
{
  "code": "7c9e2f3a-1b4d-4e8f-a5c6-3d7e9f0a1b2c",
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
| `code` | String | O | sessionStorage에 저장해둔 일회용 코드 |
| `name` | String | O | 사용자가 입력한 이름 |
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
const code = sessionStorage.getItem('oauthCode');

// 1. JSON request 데이터 구성
const requestData = {
  code: code,
  name: '홍길동',                    // 폼에서 입력받은 값
  birthDate: '2000-01-01',          // 폼에서 입력받은 값 (YYYY-MM-DD)
  gender: 'MALE',                   // 폼에서 입력받은 값
  jobPositions: ['WEB_SERVER', 'WEB_FRONTEND'],  // 폼에서 선택한 값
  projectExperienceCount: 3,        // 폼에서 입력받은 값
  skills: ['Java', 'Spring Boot'],  // 폼에서 선택한 값 (선택)
  githubUrl: 'https://github.com/username',  // (선택)
  blogUrl: 'https://blog.example.com'        // (선택)
};

// 2. FormData 구성
const formData = new FormData();

// request part: JSON을 Blob으로 감싸서 넣어야 합니다
formData.append(
  'request',
  new Blob([JSON.stringify(requestData)], { type: 'application/json' })
);

// file part: 프로필 이미지 (선택 — 없으면 이 줄을 생략)
if (profileImageFile) {
  formData.append('file', profileImageFile);
}

// 3. 요청
const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/register/oauth2', {
  method: 'POST',
  credentials: 'include',   // 반드시 필요
  body: formData,
  // Content-Type 헤더를 직접 설정하면 안 됩니다!
  // FormData를 사용하면 브라우저가 boundary를 포함해서 자동 설정합니다
});
```

> **주의:** `headers: { 'Content-Type': 'multipart/form-data' }`를 직접 설정하면 **요청이 실패합니다.**
> FormData를 body에 넣으면 브라우저가 자동으로 설정합니다.

### 성공 응답 (HTTP 200)

백엔드에서 **2가지**가 동시에 옵니다:

#### (A) 응답 바디 — JSON

```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "username": "홍길동",
    "memberId": 42,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImY0ZjVjNjRkLTBiMzMtNDA5MS..."
  }
}
```

| 경로 | 타입 | 설명 |
| --- | --- | --- |
| `code` | String | 응답 상태 코드 (`"COMMON200"`) |
| `message` | String | 응답 메시지 |
| `result.username` | String | 가입한 회원의 이름 |
| `result.memberId` | Long | 가입한 회원의 ID |
| `result.accessToken` | String | **이후 API 호출에 사용할 인증 토큰** |

#### (B) Set-Cookie 헤더 — 브라우저가 자동 처리

```
Set-Cookie: refreshToken=eyJhbGciOi...; Path=/; Domain=.meeteam.alom-sejong.com; HttpOnly; Secure; SameSite=None
```

### 성공 시 처리 코드

```jsx
const data = await response.json();

// 1. 저장해둔 code 정리
sessionStorage.removeItem('oauthCode');

// 2. accessToken 저장
localStorage.setItem('accessToken', data.result.accessToken);

// 3. 메인 페이지로 이동
navigate('/');
```

### 실패 응답

#### 코드 만료 또는 이미 사용됨 (HTTP 400)

```json
{
  "code": "OAUTH404",
  "message": "유효하지 않거나 만료된 OAuth 인증 코드입니다."
}
```

**언제 발생하나요?**

- 가입 코드는 **10분** 후에 만료됩니다
- 코드를 이미 한 번 사용한 경우
- code 값이 잘못된 경우

#### 이미 가입된 계정 (HTTP 400)

```json
{
  "code": "MEMBER400",
  "message": "이미 존재하는 회원입니다."
}
```

#### 존재하지 않는 기술스택 (HTTP 404)

```json
{
  "code": "SKILL404",
  "message": "해당 기술스택이 존재하지 않습니다."
}
```

#### 필수 필드 누락 (HTTP 400)

```json
{
  "code": "COMMON400",
  "message": "입력값이 올바르지 않습니다."
}
```

### 실패 시 처리 코드

```jsx
if (!response.ok) {
  const errorData = await response.json();
  switch (errorData.code) {
    case 'OAUTH404':
      // 코드 만료 → 소셜 로그인을 처음부터 다시
      sessionStorage.removeItem('oauthCode');
      alert('인증이 만료되었습니다. 다시 소셜 로그인을 해주세요.');
      navigate('/login');
      break;
    case 'MEMBER400':
      // 이미 가입된 계정 → 로그인 페이지로
      sessionStorage.removeItem('oauthCode');
      alert('이미 가입된 계정입니다. 로그인해주세요.');
      navigate('/login');
      break;
    case 'SKILL404':
      // 입력값 오류 → 폼에서 수정하도록
      alert(errorData.message);
      break;
    default:
      alert(errorData.message || '회원가입에 실패했습니다.');
      break;
  }
  return;
}
```

### 전체 함수 (복사용)

```jsx
async function submitOAuthSignup({ name, birthDate, gender, jobPositions, projectExperienceCount, skills, githubUrl, blogUrl, profileImage }) {
  const code = sessionStorage.getItem('oauthCode');
  if (!code) {
    alert('인증 정보가 만료되었습니다. 다시 소셜 로그인을 해주세요.');
    navigate('/login');
    return;
  }

  // request JSON 구성
  const requestData = {
    code,
    name,
    birthDate,
    gender,
    jobPositions,
    projectExperienceCount,
    skills,        // 선택
    githubUrl,     // 선택
    blogUrl        // 선택
  };

  // FormData 구성
  const formData = new FormData();
  formData.append(
    'request',
    new Blob([JSON.stringify(requestData)], { type: 'application/json' })
  );
  if (profileImage) {
    formData.append('file', profileImage);
  }

  try {
    const response = await fetch('https://api.meeteam.alom-sejong.com/api/auth/register/oauth2', {
      method: 'POST',
      credentials: 'include',
      body: formData,
    });

    if (!response.ok) {
      const errorData = await response.json();
      switch (errorData.code) {
        case 'OAUTH404':
          sessionStorage.removeItem('oauthCode');
          alert('인증이 만료되었습니다. 다시 소셜 로그인을 해주세요.');
          navigate('/login');
          return;
        case 'MEMBER400':
          sessionStorage.removeItem('oauthCode');
          alert('이미 가입된 계정입니다. 로그인해주세요.');
          navigate('/login');
          return;
        default:
          alert(errorData.message || '회원가입에 실패했습니다.');
          return;
      }
    }

    const data = await response.json();
    console.log('가입 완료:', data.result.username, data.result.memberId);

    // code 정리
    sessionStorage.removeItem('oauthCode');

    // accessToken 저장
    localStorage.setItem('accessToken', data.result.accessToken);

    // 메인 페이지로 이동
    navigate('/');
  } catch (error) {
    console.error('회원가입 중 네트워크 오류:', error);
    alert('네트워크 오류가 발생했습니다.');
  }
}
```

### 함수 호출 예시 (회원가입 폼 submit 핸들러)

```jsx
// React 예시
function OAuthSignupForm() {
  const [name, setName] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [gender, setGender] = useState('');
  const [jobPositions, setJobPositions] = useState([]);
  const [projectExperienceCount, setProjectExperienceCount] = useState(0);
  const [skills, setSkills] = useState([]);
  const [githubUrl, setGithubUrl] = useState('');
  const [blogUrl, setBlogUrl] = useState('');
  const [profileImage, setProfileImage] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    await submitOAuthSignup({
      name,
      birthDate,                              // "2000-01-01" 형식
      gender,                                 // "MALE" 또는 "FEMALE"
      jobPositions,                           // ["WEB_SERVER", "AI"]
      projectExperienceCount: Number(projectExperienceCount),
      skills: skills.length > 0 ? skills : undefined,    // 선택
      githubUrl: githubUrl || undefined,                 // 선택
      blogUrl: blogUrl || undefined,                     // 선택
      profileImage,                                      // File 객체 또는 null
    });
  };

  return (
    <form onSubmit={handleSubmit}>
      <input value={name} onChange={e => setName(e.target.value)} placeholder="이름" required />
      <input value={birthDate} onChange={e => setBirthDate(e.target.value)} type="date" required />
      <select value={gender} onChange={e => setGender(e.target.value)} required>
        <option value="">성별 선택</option>
        <option value="MALE">남성</option>
        <option value="FEMALE">여성</option>
      </select>
      {/* jobPositions 체크박스 */}
      {/* projectExperienceCount 입력 */}
      {/* skills 선택 (선택사항) */}
      {/* githubUrl, blogUrl 입력 (선택사항) */}
      <input type="file" onChange={e => setProfileImage(e.target.files[0])} />
      <button type="submit">가입하기</button>
    </form>
  );
}
```

---

## 참고: 응답 구조 패턴 정리

백엔드의 모든 응답은 아래 두 가지 형태 중 하나입니다.

### 성공 시

```json
{
  "code": "성공코드",
  "message": "성공 메시지",
  "result": { ... }          // API마다 다름. 없을 수도 있음
}
```

HTTP 상태: `200`

### 실패 시

```json
{
  "code": "에러코드",
  "message": "에러 메시지"
}
```

HTTP 상태: `400`, `401`, `404`, `500` 등 (에러에 따라 다름)

> 성공/실패를 구분하려면 `response.ok` (HTTP 상태 200번대인지)를 확인하면 됩니다.
> `code` 문자열로 세부 분기가 필요할 때만 `code`를 확인하세요.

---

## 이 문서에서 사용된 에러 코드 총정리

| code | HTTP 상태 | message | 의미 |
| --- | --- | --- | --- |
| `OAUTH404` | 400 | 유효하지 않거나 만료된 OAuth 인증 코드입니다. | code가 만료(60초/10분)되었거나 이미 사용됨 |
| `MEMBER400` | 400 | 이미 존재하는 회원입니다. | 같은 소셜 계정으로 이미 가입 완료 |
| `SKILL404` | 404 | 해당 기술스택이 존재하지 않습니다. | skills에 잘못된 값 |
| `COMMON400` | 400 | 입력값이 올바르지 않습니다. | 필수 필드 누락 등 validation 실패 |
| `MEMBER404` | 404 | 회원을 찾을 수 없습니다. | 토큰 교환 시 회원이 삭제된 경우 (거의 없음) |
