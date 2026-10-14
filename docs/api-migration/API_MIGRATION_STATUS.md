# API 마이그레이션 현황

> 마지막 업데이트: 2026-03-18
> 현재 브랜치: `feat/프로젝트-관리`

## 개요

이 문서는 v1 API로 마이그레이션 완료된 API와 아직 Legacy 상태인 API를 정리합니다.

---

## v1 API (마이그레이션 완료)

### Project 관련

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectController | `POST` | `/api/v1/projects` | 프로젝트 생성 | ✅ 완료 |
| ProjectController | `GET` | `/api/v1/projects/{projectId}` | 프로젝트 상세 조회 | ✅ 완료 |

### Project Management (프로젝트 관리)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectManagementController | `POST` | `/api/v1/projects/{projectId}/recruitment/toggle` | 모집 상태 토글 | ✅ 완료 |
| ProjectManagementController | `GET` | `/api/v1/projects/{projectId}/team` | 팀원 관리 정보 조회 | ✅ 완료 |
| ProjectManagementController | `GET` | `/api/v1/projects/{projectId}/edit` | 프로젝트 수정 Pre-fill 조회 | ✅ 완료 |
| ProjectManagementController | `PUT` | `/api/v1/projects/{projectId}` | 프로젝트 수정 | ✅ 완료 |
| ProjectManagementController | `DELETE` | `/api/v1/projects/{projectId}/members/{memberId}` | 팀원 방출 | ✅ 완료 |

### Application (프로젝트 지원)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ApplicationController | `POST` | `/api/v1/projects/{projectId}/application` | 프로젝트 지원 | ✅ 완료 |
| ApplicationController | `GET` | `/api/v1/projects/{projectId}/applications` | 지원자 목록 조회 | ✅ 완료 |
| ApplicationController | `GET` | `/api/v1/projects/{projectId}/applications/{applicationId}` | 지원 상세 조회 | ✅ 완료 |
| ApplicationController | `POST` | `/api/v1/projects/{projectId}/applications/{applicationId}/decision` | 지원 승인/거절 | ✅ 완료 |
| ApplicationController | `GET` | `/api/v1/members/me/applications` | 내가 지원한 프로젝트 조회 | ✅ 완료 |

### Project Like (좋아요)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectLikeController | `POST` | `/api/v1/project/like/{projectId}` | 좋아요 토글 | ✅ 완료 |
| ProjectLikeController | `GET` | `/api/v1/project/like/{projectId}` | 좋아요 상태 조회 | ✅ 완료 |

### Project Q&A

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| ProjectQnaController | `GET` | `/api/v1/projects/{projectId}/qna` | Q&A 목록 조회 | ✅ 완료 |
| ProjectQnaController | `POST` | `/api/v1/projects/{projectId}/qna` | 질문 등록 | ✅ 완료 |
| ProjectQnaController | `POST` | `/api/v1/projects/{projectId}/qna/{qnaId}/answer` | 답변 등록 | ✅ 완료 |

### Job (직군/직무/기술스택)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| JobController | `GET` | `/api/v1/jobs/options` | 직군 정보 전체 조회 | ✅ 완료 |

### Main Page

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| MainPageController | `GET` | `/api/v1/main/projects` | 메인 페이지 프로젝트 카드 조회 | ✅ 완료 |

### Auth (인증)

| Controller | Method | Endpoint | 설명 | 상태 |
|------------|--------|----------|------|------|
| AuthController | `POST` | `/api/v1/auth/register` | 회원가입 | ✅ 완료 |
| AuthController | `POST` | `/api/v1/auth/register/oauth2` | OAuth2 회원가입 | ✅ 완료 |
| AuthController | `POST` | `/api/v1/auth/token/exchange` | OAuth2 토큰 교환 | ✅ 완료 |
| AuthController | `POST` | `/api/v1/auth/register/{memberId}` | 자기소개 등록 | ✅ 완료 |
| AuthController | `POST` | `/api/v1/auth/email` | 이메일 중복 체크 | ✅ 완료 |
| AuthController | `POST` | `/api/v1/auth/refresh` | 토큰 재발급 | ✅ 완료 |
| AuthController | `POST` | `/api/v1/auth/logout` | 로그아웃 | ✅ 완료 |

---

## Legacy API (마이그레이션 필요)

### Project 관련

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| ProjectController | `DELETE` | `/api/projects/{projectId}` | 프로젝트 삭제 | 🔴 높음 |
| ProjectController | `GET` | `/api/projects/my` | 내 프로젝트 조회 | 🔴 높음 |
| ProjectController | `GET` | `/api/projects` | 프로젝트 목록 조회 | 🟡 중간 |
| ProjectQueryController | `GET` | `/api/projects/condition` | 프로젝트 조건 검색 | 🟡 중간 |

### Project Management (레포지토리)

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| ProjectManagementController | `POST` | `/api/projects/{projectId}/repos` | GitHub 레포 연결 | 🟢 낮음 |
| ProjectManagementController | `GET` | `/api/projects/{projectId}/repos` | 레포 목록 조회 | 🟢 낮음 |

### Application (Deprecated)

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| ApplicationController | `GET` | `/api/projects/my/applications` | 내가 지원한 프로젝트 (v1 대체 완료) | ⚫ 삭제 예정 |

### Project Member

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| ProjectMemberController | `GET` | `/api/project-members/{projectId}` | 프로젝트 멤버 전체 조회 | 🔴 높음 |
| ProjectMemberController | `POST` | `/api/project-members` | 프로젝트 멤버 삭제(추방) | ✅ v1 완료 |
| ProjectMemberController | `POST` | `/api/project-members/withdraw` | 프로젝트 멤버 자진 탈퇴 | 🔴 높음 |

### Member

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| MemberController | `GET` | `/api/members` | 나의 프로필 보기 | 🔴 높음 |
| MemberController | `PUT` | `/api/members` | 나의 프로필 수정 | 🔴 높음 |
| MemberController | `GET` | `/api/members/{memberId}` | 특정 사용자 프로필 보기 | 🔴 높음 |
| MemberController | `GET` | `/api/members/all` | 메인 페이지 사용자 카드 조회 | 🟡 중간 |
| MemberController | `GET` | `/api/members/search` | 사용자 조건 검색 | 🟡 중간 |

### Notification

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| NotificationController | `GET` | `/api/subscribe` | SSE 알림 구독 | 🟡 중간 |
| NotificationController | `GET` | `/api/notifications` | 전체 알림 조회 | 🟡 중간 |
| NotificationController | `GET` | `/api/notifications/unread/count` | 읽지 않은 알림 수 조회 | 🟡 중간 |

### Code Review

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| CodeReviewController | `POST` | `/api/codereviews/start` | PR 리뷰 시작 | 🟢 낮음 |

### Pull Request

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| PullRequestController | `GET` | `/api/prs/{owner}/{repo}/{prNumber}` | 특정 PR 조회 | 🟢 낮음 |
| PullRequestController | `GET` | `/api/prs/{projectId}` | 프로젝트 내 모든 PR 조회 | 🟢 낮음 |
| PullRequestController | `GET` | `/api/prs/{owner}/{repo}` | 레포 내 모든 PR 조회 | 🟢 낮음 |

### Webhook

| Controller | Method | Endpoint | 설명 | 우선순위 |
|------------|--------|----------|------|----------|
| GithubWebhookController | `POST` | `/api/webhooks/github` | GitHub Webhook 처리 | 🟢 낮음 |

---

## 마이그레이션 우선순위 기준

| 우선순위 | 설명 |
|----------|------|
| 🔴 높음 | 프론트엔드에서 자주 사용, 핵심 기능 |
| 🟡 중간 | 사용 빈도 보통, 부가 기능 |
| 🟢 낮음 | 내부용 또는 특수 기능 (GitHub 연동 등) |
| ⚫ 삭제 예정 | v1 대체 API가 이미 존재, 제거 예정 |

---

## 마이그레이션 작업 체크리스트

### 다음 작업 예정

- [ ] `/api/projects/{projectId}` (DELETE) → `/api/v1/projects/{projectId}` (DELETE)
- [ ] `/api/projects/my` → `/api/v1/members/me/projects`
- [ ] `/api/project-members` → `/api/v1/projects/{projectId}/members`
- [ ] `/api/members` → `/api/v1/members/me`
- [ ] `/api/members/{memberId}` → `/api/v1/members/{memberId}`

### 완료된 작업

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
- Legacy API는 점진적으로 `@Deprecated` 처리 후 삭제 예정
- 프론트엔드와 협의하여 마이그레이션 일정 조율 필요
