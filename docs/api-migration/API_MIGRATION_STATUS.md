# API 마이그레이션 현황

> 검증 기준: 공개 코드 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`
> 정적 코드 대조 결과입니다. 배포 상태, 테스트 실행·통과, 프론트엔드 전환 완료를 의미하지 않습니다.

## 개요

이 문서는 기준 커밋에 선언된 v1 API와 남아 있는 Legacy API를 정리합니다. 구현 존재와 기존 API 제거는 구분합니다.

---

## v1 API (라우트 구현 확인)

### Project 관련

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectController | `POST` | `/api/v1/projects` | 프로젝트 생성 | 구현 확인 |
| ProjectController | `GET` | `/api/v1/projects/{projectId}` | 프로젝트 상세 조회 | 구현 확인 |
| ProjectController | `DELETE` | `/api/v1/projects/{projectId}` | 프로젝트 삭제 | 구현 확인, Legacy도 잔존 |
| ProjectQueryController | `GET` | `/api/v1/projects/search` | 프로젝트 조건 검색 | 구현 확인, Legacy도 잔존 |

### Project Management (프로젝트 관리)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectManagementController | `POST` | `/api/v1/projects/{projectId}/recruitment/toggle` | 모집 상태 토글 | 구현 확인 |
| ProjectManagementController | `GET` | `/api/v1/projects/{projectId}/team` | 팀원 관리 정보 조회 | 구현 확인 |
| ProjectManagementController | `GET` | `/api/v1/projects/{projectId}/edit` | 프로젝트 수정 Pre-fill 조회 | 구현 확인 |
| ProjectManagementController | `PUT` | `/api/v1/projects/{projectId}` | 프로젝트 수정 | 구현 확인 |
| ProjectManagementController | `DELETE` | `/api/v1/projects/{projectId}/members/{memberId}` | 팀원 방출 | 구현 확인 |

### Application (프로젝트 지원)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ApplicationController | `GET` | `/api/v1/projects/{projectId}/application` | 지원 페이지 조회 | 구현 확인 |
| ApplicationController | `POST` | `/api/v1/projects/{projectId}/application` | 프로젝트 지원 | 구현 확인 |
| ApplicationController | `GET` | `/api/v1/projects/{projectId}/applications` | 지원자 목록 조회 | 구현 확인 |
| ApplicationController | `GET` | `/api/v1/projects/{projectId}/applications/{applicationId}` | 지원 상세 조회 | 구현 확인 |
| ApplicationController | `POST` | `/api/v1/projects/{projectId}/applications/{applicationId}/decision` | 지원 승인/거절 | 구현 확인 |
| ApplicationController | `GET` | `/api/v1/members/me/applications` | 내가 지원한 프로젝트 조회 | 구현 확인 |
| ApplicationController | `DELETE` | `/api/v1/members/me/applications/{applicationId}` | 지원 취소 | 구현 확인 |

### Project Like (좋아요)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectLikeController | `POST` | `/api/v1/project/like/{projectId}` | 좋아요 토글 | 구현 확인 |
| ProjectLikeController | `GET` | `/api/v1/project/like/{projectId}` | 좋아요 상태 조회 | 구현 확인 |

### Project Q&A

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectQnaController | `GET` | `/api/v1/projects/{projectId}/qna` | Q&A 목록 조회 | 구현 확인 |
| ProjectQnaController | `POST` | `/api/v1/projects/{projectId}/qna` | 질문 등록 | 구현 확인 |
| ProjectQnaController | `POST` | `/api/v1/projects/{projectId}/qna/{qnaId}/answer` | 답변 등록 | 구현 확인 |
| ProjectQnaController | `DELETE` | `/api/v1/projects/{projectId}/qna/{qnaId}` | 질문 삭제 | 구현 확인 |
| ProjectQnaController | `DELETE` | `/api/v1/projects/{projectId}/qna/{qnaId}/answers/{answerId}` | 답변 삭제 | 구현 확인 |

### Job (직군/직무/기술스택)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| JobController | `GET` | `/api/v1/jobs/options` | 직군 정보 전체 조회 | 구현 확인 |

### Main Page

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| MainPageController | `GET` | `/api/v1/main/projects` | 메인 페이지 프로젝트 카드 조회 | 구현 확인 |
| MainPageController | `GET` | `/api/v1/main/members` | 메인 페이지 회원 카드 조회 | 구현 확인 |

### Auth (인증)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| AuthController | `POST` | `/api/v1/auth/login/sejong` | 세종대 로그인 | 구현 확인 |
| AuthController | `POST` | `/api/v1/auth/register/sejong` | 세종대 회원가입 (multipart) | 구현 확인 |
| AuthController | `POST` | `/api/v1/auth/register/{memberId}` | 자기소개 등록 | 구현 확인 |
| AuthController | `POST` | `/api/v1/auth/refresh` | 토큰 재발급 | 구현 확인 |
| AuthController | `POST` | `/api/v1/auth/logout` | 로그아웃 | 구현 확인 |
| AuthController | `DELETE` | `/api/v1/auth/withdraw` | 회원 탈퇴 | 구현 확인 |
| AuthController | `DELETE` | `/api/v1/auth/delete/{memberId}` | 회원 하드 삭제 | 구현 확인, 접근 권한은 별도 보안 정책 확인 필요 |
| TestAccountController | `POST` | `/api/v1/auth/login/test` | 테스트 계정 로그인 | test 프로필이며 prod/production 제외, test-account.enabled=true 조건 |

### Member / Notification

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| MemberController | `GET` | `/api/v1/members/{memberId}` | 회원 상세 | 구현 확인 |
| MemberController | `GET` | `/api/v1/members/me` | 내 프로필 | 구현 확인 |
| MemberController | `PUT` | `/api/v1/members/me` | 내 프로필 수정 (multipart) | 구현 확인 |
| MemberController | `GET` | `/api/v1/members/search` | 회원 검색 | 구현 확인 |
| NotificationController | `GET` | `/api/v1/subscribe` | SSE 구독 | 구현 확인 |
| NotificationController | `GET` | `/api/v1/notifications` | 알림 조회 | 구현 확인 |
| NotificationController | `GET` | `/api/v1/notifications/unread/count` | 미확인 알림 수 | 구현 확인 |

---

## Legacy API (기준 커밋에 잔존)

### Project 관련

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| ProjectController | `DELETE` | `/api/projects/{projectId}` | 프로젝트 삭제 | v1 존재, Deprecated 잔존 |
| ProjectController | `GET` | `/api/projects/my` | 내 프로젝트 조회 | 🔴 높음 |
| ProjectController | `GET` | `/api/projects` | 프로젝트 목록 조회 | 🟡 중간 |
| ProjectQueryController | `GET` | `/api/projects/condition` | 프로젝트 조건 검색 | v1 존재, Deprecated 잔존 |

### Application (Deprecated)

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| ApplicationController | `GET` | `/api/projects/my/applications` | 내가 지원한 프로젝트 (v1 대체 라우트 존재) | ⚫ 삭제 검토 |

### Project Member

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| ProjectMemberController | `GET` | `/api/project-members/{projectId}` | 프로젝트 멤버 전체 조회 | 🔴 높음 |
| ProjectMemberController | `POST` | `/api/project-members` | 프로젝트 멤버 삭제(추방) | v1 존재, Deprecated 잔존 |
| ProjectMemberController | `POST` | `/api/project-members/withdraw` | 프로젝트 멤버 자진 탈퇴 | 🔴 높음 |

### 제거되어 현재 라우트가 없는 항목

기존 회원·알림 비버전 경로, GitHub 레포 연결, CodeReviewController, PullRequestController, GithubWebhookController는 기준 커밋의 컨트롤러에 없습니다. 미완료 Legacy 목록이 아니라 제거된 과거 항목입니다. 일반/OAuth2 회원가입, 토큰 교환, 이메일 중복 확인 경로도 현재 AuthController에 없습니다.

---

## 마이그레이션 우선순위 기준

아래 우선순위는 기존 문서의 제안이며 실제 이용 빈도나 합의된 일정을 검증한 결과는 아닙니다.

| 우선순위 | 설명 |
|----------|------|
| 🔴 높음 | 프론트엔드에서 자주 사용, 핵심 기능 |
| 🟡 중간 | 사용 빈도 보통, 부가 기능 |
| 🟢 낮음 | 내부용 또는 특수 기능 (GitHub 연동 등) |
| ⚫ 삭제 검토 | v1 대체 API가 이미 존재, 제거 일정 미확인 |

---

## 마이그레이션 작업 체크리스트

### 구현 여부와 남은 계획

- [x] 프로젝트 삭제 v1 라우트 구현 (Legacy 삭제는 미완료)
- [ ] `/api/projects/my` → `/api/v1/members/me/projects`
- [x] 팀원 방출 v1 라우트 구현: `DELETE /api/v1/projects/{projectId}/members/{memberId}` (Legacy 잔존)
- [x] 내 프로필 조회·수정 v1 라우트 구현
- [x] 회원 상세 v1 라우트 구현

### v1 라우트 구현 확인 (실행 검증 아님)

- [x] 프로젝트 생성 API v1 마이그레이션
- [x] 프로젝트 상세 조회 API v1 마이그레이션
- [x] 프로젝트 관리 API (모집상태, 팀원관리, 수정) v1 마이그레이션
- [x] 프로젝트 지원 관련 API v1 마이그레이션
- [x] 프로젝트 좋아요 API v1 마이그레이션
- [x] 프로젝트 Q&A API v1 마이그레이션
- [x] 메인 페이지 API v1 마이그레이션
- [x] 인증 API v1 마이그레이션

---

## 참고사항

- 모든 v1 API는 `/api/v1/` prefix를 사용
- 표에 남은 Legacy API는 모두 `@Deprecated` 선언이 있으며 실제 삭제·클라이언트 전환 일정은 미확인
- 프론트엔드와 협의하여 마이그레이션 일정 조율 필요

## 근거 및 미확인 사항

- `src/main/java/com/wardk/meeteam_backend/web/` 아래 각 `controller/*Controller.java`의 Mapping 선언 기준입니다.
- `/api/v1/members/me/projects`는 기준 커밋에 없습니다. 팀원 자진 탈퇴 전용 v1 라우트도 확인되지 않습니다. 대체 API 설계와 Legacy 제거는 남은 계획입니다.
- 구현 확인 체크는 테스트 통과·운영 배포·프론트엔드 연동 검증을 뜻하지 않습니다. 이 문서 점검에서는 테스트를 실행하지 않았습니다.

### 주요 라우트 근거 (기준 커밋 줄 번호)

- `web/project/controller/ProjectController.java:59-96`: v1 삭제와 잔존 Deprecated 삭제·내 프로젝트·전체 목록.
- `web/project/controller/ProjectQueryController.java:31-48`: v1 검색과 Deprecated 조건 검색.
- `web/member/controller/MemberController.java:42-96`: 회원 상세·검색·내 프로필 조회·수정.
- `web/auth/controller/AuthController.java:35-157`: 현재 세종대 인증·자기소개·갱신·로그아웃·탈퇴·삭제.
- `web/auth/controller/TestAccountController.java:19-34`: 테스트 로그인 활성 조건과 경로.
- `web/application/controller/ApplicationController.java:25-108`: 지원 페이지·취소·잔존 Legacy.
- `web/notification/controller/NotificationController.java:41-64`, `web/qna/controller/ProjectQnaController.java:102-117`: v1 알림 및 Q&A 삭제.
- Java 경로의 루트: `src/main/java/com/wardk/meeteam_backend/`.
