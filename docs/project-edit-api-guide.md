# 프로젝트 수정 API 가이드 (Frontend)

## 기준과 범위

기준 소스는 공개 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`입니다. `git show <commit>:<path>`로 확인한 **백엔드의 정적 계약**이며, API 실행·배포 환경·프론트엔드 구현을 검증한 문서가 아닙니다. UI 흐름과 JavaScript는 연동 권장안입니다.

> 생성 API의 `projectName`과 혼동하지 마세요. 이 커밋의 **수정 요청·Pre-fill·수정 성공 응답은 모두 `name`**입니다. Swagger 예시의 `IT_SERVICE`도 실제 enum에는 없습니다.

## 1. 인증과 전체 흐름

1. `GET /api/v1/jobs/options`로 직군·포지션·기술 스택 선택지를 조회합니다.
2. `GET /api/v1/projects/{projectId}/edit`로 수정용 정보를 조회합니다. 리더만 가능합니다.
3. 폼을 구성하고 `PUT /api/v1/projects/{projectId}`로 전체 모집 목록을 전송합니다.

인증은 `Bearer `로 시작하는 Authorization 헤더 우선이며, 그런 헤더가 없으면 `accessToken` 쿠키를 사용합니다. Bearer 토큰이 잘못되어도 유효한 쿠키로 재시도하지 않습니다. 쿠키는 HttpOnly이므로 JavaScript로 읽어 헤더에 복사하지 않습니다. 쿠키 연동 시 `credentials: "include"`를 사용하되, 실제 교차 출처 허용 여부는 서버 CORS·쿠키 설정과 브라우저 정책에 달려 있습니다. 쿠키의 Secure·SameSite·domain은 설정값을 사용하며 고정값으로 가정하면 안 됩니다.

```http
GET /api/v1/projects/{projectId}/edit
Cookie: accessToken=<accessToken>
```

헤더 방식이면 Cookie 대신 `Authorization: Bearer <accessToken>`을 보냅니다. 위 토큰은 자리표시자입니다.

## 2. Pre-fill 성공 응답 (HTTP 200)

아래 JSON은 **템플릿**입니다. `<projectId>`, `<existingRecruitmentStateId>`, `<techStackId>`는 실제 숫자(Long)로 바꾸고 따옴표를 제거합니다. 이름·URL·기술 스택 표시명도 해당 데이터의 값입니다. 동적 ID를 하드코딩하지 않습니다.

```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "projectId": "<projectId>",
    "name": "MeeTeam 프로젝트",
    "description": "개발자 팀 매칭 플랫폼입니다.",
    "projectCategory": "CAPSTONE",
    "projectCategoryName": "캡스톤",
    "platformCategory": "WEB",
    "githubRepositoryUrl": "https://github.com/example/meeteam",
    "communicationChannelUrl": "https://discord.gg/abc123",
    "endDate": null,
    "imageUrl": null,
    "leaderJobFieldName": "백엔드",
    "leaderJobPositionName": "Java/Spring",
    "recruitments": [
      {
        "recruitmentStateId": "<existingRecruitmentStateId>",
        "jobFieldCode": "BACKEND",
        "jobFieldName": "백엔드",
        "jobPositionCode": "JAVA_SPRING",
        "jobPositionName": "Java/Spring",
        "recruitmentCount": 3,
        "currentCount": 2,
        "pendingApplicationCount": 1,
        "techStackIds": ["<techStackId>"],
        "techStackNames": ["Java"],
        "deletable": false,
        "notDeletableReason": "승인된 팀원이 있는 포지션은 삭제할 수 없습니다.",
        "minRecruitmentCount": 2
      }
    ],
    "editable": true,
    "notEditableReason": null
  }
}
```

성공 wrapper는 `code`, `message`, `result`입니다. `isSuccess`, `data`는 없습니다.

| 필드 | 실제 의미 / 연동 주의 |
|---|---|
| `projectId`, `name`, `description` | 프로젝트 ID·이름·설명 |
| `projectCategory`, `projectCategoryName` | enum 코드와 표시명 |
| `platformCategory` | 플랫폼 enum 코드 |
| `githubRepositoryUrl`, `communicationChannelUrl` | 저장된 외부 링크 |
| `endDate`, `imageUrl` | 마감일(`YYYY-MM-DD`)·이미지 URL, null 가능 |
| `leaderJobFieldName`, `leaderJobPositionName` | 리더 ProjectMember의 표시명. **코드는 반환하지 않음** |
| `editable`, `notEditableReason` | SUSPENDED이면 false와 모집 재개 안내. Pre-fill 자체는 리더에게 반환됨 |
| `recruitments[].recruitmentStateId` | 기존 모집 행 ID. 수정 요청에서 그대로 보존 |
| `jobFieldCode/Name`, `jobPositionCode/Name` | 해당 모집 행의 직군·포지션 코드/표시명 |
| `recruitmentCount`, `currentCount` | 정원·현재 승인 인원 |
| `pendingApplicationCount` | 해당 프로젝트·포지션의 PENDING 지원서 수 |
| `techStackIds`, `techStackNames` | 모집 행에 연결된 기술 스택 ID·이름 목록 |
| `deletable`, `notDeletableReason` | currentCount가 0일 때만 삭제 가능. 대기 지원자 확인은 별도 |
| `minRecruitmentCount` | currentCount 그대로. 요청 DTO 최소값도 적용하므로 UI 최솟값은 `Math.max(1, minRecruitmentCount)` 권장 |

### Pre-fill의 누락 필드 주의

- PUT 필수인 `recruitmentDeadlineType`, `leaderJobPositionCode`가 Pre-fill에는 없습니다.
- 마감 방식은 `GET /api/v1/projects/{projectId}`의 `result.recruitmentDeadlineType`으로 보완할 수 있습니다. `endDate`만으로 추정하지 않는 것을 권장합니다.
- 리더 코드는 jobs options와 확인된 리더 포지션을 바탕으로 선택·확보해야 합니다. 표시명을 코드처럼 전송하면 안 됩니다.
- 특히 `leaderJobPositionCode`는 요청에서 필수지만, 이 커밋 수정 서비스는 이를 적용하지 않습니다. 리더 포지션 변경 완료를 UI에서 약속하지 마세요.

## 3. 수정 요청

```http
PUT /api/v1/projects/{projectId}
Cookie: accessToken=<accessToken>
Content-Type: multipart/form-data; boundary=<browser-generated-boundary>
```

| Part | 필수 | 형식 |
|---|---|---|
| `request` | O | `Content-Type: application/json`인 JSON part |
| `file` | X | 새 대표 이미지 파일. 생략 또는 빈 파일이면 기존 이미지 유지 |

### request 전체 예시

기존 행 하나를 유지하고 신규 행 하나를 추가하는 템플릿입니다. ID 자리표시자는 실제 숫자로 치환합니다. 기존 행을 삭제하려면 배열에서 **그 행 전체를 제외**합니다.

```json
{
  "name": "수정된 프로젝트명",
  "description": "수정된 설명",
  "projectCategory": "CAPSTONE",
  "platformCategory": "WEB",
  "githubRepositoryUrl": "https://github.com/example/meeteam",
  "communicationChannelUrl": "https://discord.gg/abc123",
  "endDate": null,
  "recruitmentDeadlineType": "RECRUITMENT_COMPLETED",
  "leaderJobPositionCode": "JAVA_SPRING",
  "recruitments": [
    {
      "recruitmentStateId": "<existingRecruitmentStateId>",
      "jobFieldCode": "BACKEND",
      "jobPositionCode": "JAVA_SPRING",
      "recruitmentCount": 4,
      "techStackIds": ["<backendTechStackId>"]
    },
    {
      "recruitmentStateId": null,
      "jobFieldCode": "INFRA_OPERATION",
      "jobPositionCode": "DEVOPS_ARCHITECT",
      "recruitmentCount": 1,
      "techStackIds": ["<infraTechStackId>"]
    }
  ],
  "confirmDeletePositionsWithPendingApplicants": false
}
```

| 필드 | 요청 검증 / 실제 처리 |
|---|---|
| `name` | 필수, 공백 불가. `projectName` 아님 |
| `description` | 선택, 최대 5,000자. null도 허용하며 기본 정보에 그대로 대입 |
| `projectCategory` | 필수: `CAPSTONE`, `CREATIVE_SEMESTER`, `CLUB`, `ETC` |
| `platformCategory` | 필수: `IOS`, `ANDROID`, `WEB` |
| `githubRepositoryUrl` | 선택(null 허용). 값이 있으면 `https://github.com/{owner}/{repo}` 형식(끝 `/` 허용). 빈 문자열은 패턴 불일치 |
| `communicationChannelUrl` | 선택(null 허용). HTTPS Discord 초대·Slack 초대/워크스페이스·Kakao 오픈채팅 패턴만 허용. 빈 문자열은 패턴 불일치 |
| `endDate` | 선택 LocalDate. 설명상 END_DATE에서 필요하지만 **수정 경로에는 날짜/마감 방식 조합 검증이 없음** |
| `recruitmentDeadlineType` | 필수: `END_DATE`, `RECRUITMENT_COMPLETED`. 의도에 맞춰 END_DATE에는 날짜, 상시모집에는 null을 보내는 것을 권장 |
| `leaderJobPositionCode` | 필수 JobPositionCode. Command로 전달되지만 리더 변경에는 사용되지 않음 |
| `recruitments` | 필수, 최소 1개. 부분 변경 목록이 아니라 유지/추가할 전체 목록 |
| `recruitments[].recruitmentStateId` | 기존 행 ID 또는 신규 null(필드 생략도 null). 알 수 없는 비-null ID는 신규 생성이 아니라 무시됨 |
| `jobFieldCode`, `jobPositionCode` | 각각 필수 enum. 기존 행의 코드 변경은 적용되지 않으며 신규 행은 jobPositionCode로 조회 |
| `recruitmentCount` | 필수 정수, 최소 1 및 기존 currentCount 이상 |
| `techStackIds` | 필수, 최소 1개 ID. 수정 서비스는 조회된 스택만 연결하며 존재/직군 일치/중복 검증을 보장하지 않음 |
| `confirmDeletePositionsWithPendingApplicants` | 선택 Boolean, 생략/null은 false. 삭제 대상에 대기 지원자가 있으면 true 필요 |

`WEB_FRONTEND`는 유효한 프론트 포지션 코드이며 `REACT`는 아닙니다. 인프라는 `INFRA_OPERATION` / `DEVOPS_ARCHITECT`이며 `INFRA` / `DEVOPS`가 아닙니다. 선택지는 jobs options를 사용하세요.

### 브라우저 전송 권장 예시 (검증된 프론트 코드 아님)

```javascript
// projectId, request, selectedFile은 화면에서 확보한 값입니다.
// request의 ID는 템플릿 문자열이 아니라 실제 숫자여야 합니다.
const formData = new FormData();
formData.append("request", new Blob([JSON.stringify(request)], {
  type: "application/json"
}));
if (selectedFile) formData.append("file", selectedFile);
const response = await fetch(`/api/v1/projects/${projectId}`, {
  method: "PUT",
  credentials: "include",
  body: formData
});
// Content-Type을 직접 지정하지 않습니다. 브라우저가 boundary를 설정합니다.
// 정상/처리된 오류는 JSON, 예상치 못한 오류는 문자열일 수 있습니다.
const contentType = response.headers.get("content-type") || "";
const body = contentType.includes("application/json")
  ? await response.json() : await response.text();
if (!response.ok) {
  throw new Error(typeof body === "string" ? body : body.message);
}
const editedProject = body.result;
```

## 4. 모집 행 처리와 UI 권장안

- 기존 ID를 유지하면 정원·기술 스택만 변경합니다. 같은 ID의 직군/포지션 교체는 지원하지 않습니다.
- `recruitmentStateId: null`이면 새 행을 만듭니다. 다른 포지션으로 바꾸려면 삭제 조건을 확인한 뒤 삭제+추가해야 합니다.
- 기존 ID가 전체 요청 목록에 없으면 삭제 대상입니다. ID 누락으로 의도치 않은 삭제를 유발하지 않도록 주의하세요.
- 삭제 대상 currentCount > 0이면 거절됩니다. 0이고 PENDING 지원서가 있으면 확인 플래그가 필요합니다.
- 플래그 true이면 삭제 대상 포지션의 PENDING 지원서를 REJECTED로 바꾸고 개수를 누적합니다. 이 경로의 별도 알림 발송은 구현되어 있지 않습니다.
- `deletable`/`editable`로 버튼·폼을 제한하고, 대기 지원자 삭제 전 확인 모달을 띄우는 것은 **UI 권장안**입니다. 실제 프론트 구현 여부는 확인하지 않았습니다.
- 조회 후 상태가 바뀔 수 있으므로 서버 오류를 처리하세요. 409를 받았다고 자동으로 true로 재전송하지 말고 사용자 확인 후 다시 보내세요.

## 5. 수정 성공 응답 (HTTP 200)

```json
{
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "projectId": "<projectId>",
    "name": "수정된 프로젝트명",
    "recruitmentStatus": "RECRUITING",
    "autoRejectedApplicantCount": 0
  }
}
```

`projectId`는 실제 응답에서 숫자입니다. `recruitmentStatus`는 수정 후 인원 기준으로 `RECRUITING` 또는 `CLOSED`가 됩니다. `autoRejectedApplicantCount`는 삭제로 자동 거절된 지원서 수입니다. `updatedAt`, `autoRejectedCount`는 응답 필드가 아닙니다.

## 6. 오류 계약

처리된 비즈니스 오류는 `code`, `message`만 반환합니다. enum 상수명이 아니라 아래 **직렬화된 code**로 처리합니다. 여러 오류가 같은 code를 공유하므로 code만으로 세부 원인을 구분할 수 없습니다.

| 상황 | HTTP | code | message |
|---|---|---|---|
| 프로젝트 없음 | 404 | `PROJECT404` | 프로젝트를 찾을 수 없습니다. |
| 리더 아님(GET/PUT 공통) | 403 | `PROJECT_MEMBER403` | 해당 프로젝트 관리 권한이 없습니다. |
| Pre-fill 리더 멤버 없음 | 404 | `PROJECT_MEMBER404` | 프로젝트 멤버가 존재하지 않습니다. |
| 수정 중 회원 없음 | 404 | `MEMBER404` | 회원을 찾을 수 없습니다. |
| SUSPENDED에서 PUT | 400 | `PROJECT400` | 모집이 중단된 상태에서는 수정할 수 없습니다. 모집을 재개한 후 수정해주세요. |
| currentCount 미만으로 축소 | 400 | `RECRUITMENT400` | 현재 승인된 인원보다 적게 설정할 수 없습니다. |
| 승인 인원이 있는 행 삭제 | 400 | `RECRUITMENT400` | 승인된 팀원이 있는 포지션은 삭제할 수 없습니다. |
| 대기 지원자 삭제 미확인 | **409** | `RECRUITMENT409` | 대기 중인 지원자가 있는 포지션입니다. 삭제 시 해당 지원자들의 지원이 자동 거절됩니다. |
| 신규 포지션 조회 실패 | 404 | `JOB404` | 해당 포지션을 찾을 수 없습니다. |
| Bean Validation 실패 | 400 | `COMMON400` | 첫 필드 검증 메시지 |
| 인증 토큰 없음 | 401 | `UNAUTHORIZED` | 토큰이 존재하지 않습니다. |
| 인증 토큰 무효 | 401 | `UNAUTHORIZED` | 유효하지 않은 토큰입니다. |

완전한 오류 응답 예시:

```json
{
  "code": "PROJECT_MEMBER403",
  "message": "해당 프로젝트 관리 권한이 없습니다."
}
```

```json
{
  "code": "RECRUITMENT409",
  "message": "대기 중인 지원자가 있는 포지션입니다. 삭제 시 해당 지원자들의 지원이 자동 거절됩니다."
}
```

```json
{
  "code": "COMMON400",
  "message": "리더 포지션 코드를 입력해주세요."
}
```

`PROJECT_EDIT_FORBIDDEN`은 ErrorCode에 선언되어 있지만 이 수정 경로는 `PROJECT_MEMBER_FORBIDDEN`을 던집니다. 잘못된 JSON/enum·multipart 등 모든 오류가 위 형태의 400이라고 보장하지 않습니다. 전역 핸들러의 미처리 예외 경로는 HTTP 500과 문자열 본문을 반환합니다.

## 7. 소스 근거 (기준 커밋의 줄 번호)

아래 경로는 모두 `src/main/java/com/wardk/meeteam_backend/` 기준입니다.

| 계약 | 경로:줄 |
|---|---|
| 실제 GET/PUT 라우팅·part·검증·위임 | `web/project/controller/ProjectManagementController.java:54-78` |
| 생성과 다른 이름 필드 | `web/project/dto/request/ProjectPostRequest.java:29-31`, `web/project/dto/request/ProjectEditRequest.java:26-29` |
| 수정 필드·필수값·플래그 기본값 | `web/project/dto/request/ProjectEditRequest.java:26-90` |
| 모집 필드 검증 | `web/project/dto/request/RecruitmentEditRequest.java:16-46` |
| Pre-fill 전체 필드/리더 표시명 | `web/project/dto/response/ProjectEditPrefillResponse.java:18-95` |
| 삭제 가능·min 값 계산 | `web/project/dto/response/RecruitmentEditInfo.java:60-89` |
| 실제 갱신·리더 코드 미적용·이미지 | `domain/project/service/ProjectEditServiceImpl.java:94-130,229-239` |
| 행 삭제/추가/갱신·스택 조회 | `domain/project/service/ProjectEditServiceImpl.java:133-226` |
| 기본값 대입·권한·수정 가능 상태 | `domain/project/entity/Project.java:337-365,426-447,468-471` |
| 성공 wrapper/코드·완료 응답 | `global/response/SuccessResponse.java:10-25`, `global/response/SuccessCode.java:10`, `web/project/dto/response/ProjectEditResponse.java:13-35` |
| 오류 코드/상태/메시지 | `global/response/ErrorCode.java:11,16,45,49-64,71-72` |
| 오류 본문·검증/미처리 오류 | `global/response/ErrorResponse.java:10-19`, `global/exception/handler/GlobalExceptionHandler.java:24-32,65-78,99-104` |
| 인증 우선순위·401 | `global/auth/filter/JwtFilter.java:56-86,112-129` |
| 쿠키 이름·속성·CORS | `global/util/JwtUtil.java:40-41`, `global/auth/cookie/AccessTokenCookieProvider.java:30-44`, `global/config/SecurityConfig.java:84-95` |
| enum 실제 값 | `domain/project/entity/ProjectCategory.java:8-14`, `domain/project/entity/PlatformCategory.java:3-7`, `domain/job/entity/JobPositionCode.java:24-45`, `domain/job/entity/JobFieldCode.java:14-19` |
| 링크 패턴·옵션 조회 | `global/constant/PatternConstants.java:7-23`, `web/job/controller/JobController.java:19-22` |
| 상세 응답 마감 방식 보완 | `web/project/dto/response/ProjectDetailResponse.java:42-43` |

정책과 구현상 제한은 [프로젝트 수정 정책](project-edit-policy.md)을 참고하세요.
