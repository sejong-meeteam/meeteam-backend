# 프로젝트 수정 API 가이드 (Frontend)

프론트엔드 개발자를 위한 프로젝트 수정 API 통합 가이드입니다.

---

## 전체 흐름

```
1. GET /api/v1/projects/{projectId}/edit   → Pre-fill 데이터 조회
2. 사용자가 폼에서 수정
3. PUT /api/v1/projects/{projectId}        → 수정 요청
```

---

## 1. Pre-fill 조회 API

### 요청

```
GET /api/v1/projects/{projectId}/edit
Authorization: Bearer {accessToken}
```

### 응답

```json
{
  "isSuccess": true,
  "code": "200",
  "data": {
    "projectId": 1,
    "name": "MeeTeam 프로젝트",
    "description": "개발자 팀 매칭 플랫폼입니다.",
    "projectCategory": "IT_SERVICE",
    "projectCategoryName": "IT 서비스",
    "platformCategory": "WEB",
    "githubRepositoryUrl": "https://github.com/username/repository",
    "communicationChannelUrl": "https://discord.gg/abc123",
    "endDate": "2025-12-31",
    "imageUrl": "https://s3.../image.png",
    "leaderJobFieldName": "백엔드",
    "leaderJobPositionName": "Java/Spring",
    "recruitments": [
      {
        "recruitmentStateId": 1,
        "jobFieldCode": "BACKEND",
        "jobFieldName": "백엔드",
        "jobPositionCode": "JAVA_SPRING",
        "jobPositionName": "Java/Spring",
        "recruitmentCount": 3,
        "currentCount": 2,
        "pendingApplicationCount": 1,
        "techStackIds": [30, 31, 38],
        "techStackNames": ["Java", "Spring", "JPA"],
        "deletable": false,
        "notDeletableReason": "승인된 팀원이 있는 포지션은 삭제할 수 없습니다.",
        "minRecruitmentCount": 2
      },
      {
        "recruitmentStateId": 2,
        "jobFieldCode": "FRONTEND",
        "jobFieldName": "프론트엔드",
        "jobPositionCode": "REACT",
        "jobPositionName": "React",
        "recruitmentCount": 2,
        "currentCount": 0,
        "pendingApplicationCount": 3,
        "techStackIds": [10, 11],
        "techStackNames": ["React", "TypeScript"],
        "deletable": true,
        "notDeletableReason": null,
        "minRecruitmentCount": 0
      }
    ],
    "editable": true,
    "notEditableReason": null
  }
}
```

### Pre-fill 응답 필드 설명

#### 프로젝트 기본 정보

| 필드 | 타입 | 설명 | 용도 |
|------|------|------|------|
| `projectId` | Long | 프로젝트 ID | 수정 요청 시 URL path에 사용 |
| `name` | String | 프로젝트명 | 폼에 미리 채움 |
| `description` | String | 프로젝트 설명 | 폼에 미리 채움 |
| `projectCategory` | String | 프로젝트 카테고리 코드 | 드롭다운 선택값 |
| `projectCategoryName` | String | 프로젝트 카테고리명 | 화면 표시용 |
| `platformCategory` | String | 출시 플랫폼 코드 | 드롭다운 선택값 |
| `githubRepositoryUrl` | String | GitHub 레포 URL | 폼에 미리 채움 |
| `communicationChannelUrl` | String | 소통 채널 URL | 폼에 미리 채움 |
| `endDate` | String | 마감일 (YYYY-MM-DD) | 날짜 선택기에 미리 채움 |
| `imageUrl` | String | 커버 이미지 URL | 이미지 미리보기 |

#### 리더 정보

| 필드 | 타입 | 설명 | 용도 |
|------|------|------|------|
| `leaderJobFieldName` | String | 리더 직군명 (예: "백엔드") | 화면 표시용 |
| `leaderJobPositionName` | String | 리더 포지션명 (예: "Java/Spring") | 화면 표시용 |

#### 모집 포지션 정보 (`recruitments` 배열)

| 필드 | 타입 | 설명 | 용도 |
|------|------|------|------|
| `recruitmentStateId` | Long | **모집 상태 ID** | **수정 요청 시 반드시 포함** (기존 포지션 식별용) |
| `jobFieldCode` | String | 직군 코드 | 수정 요청에 포함 |
| `jobFieldName` | String | 직군명 | 화면 표시용 |
| `jobPositionCode` | String | 포지션 코드 | 수정 요청에 포함 |
| `jobPositionName` | String | 포지션명 | 화면 표시용 |
| `recruitmentCount` | Integer | 모집 인원 | 폼에 미리 채움, 수정 가능 |
| `currentCount` | Integer | 현재 승인 인원 | 화면 표시용, 참고 정보 |
| `pendingApplicationCount` | Integer | 대기 지원자 수 | 삭제 시 경고 표시에 사용 |
| `techStackIds` | List\<Long\> | 기술 스택 ID 목록 | 수정 요청에 포함 |
| `techStackNames` | List\<String\> | 기술 스택명 목록 | 화면 표시용 |
| `deletable` | Boolean | 삭제 가능 여부 | 삭제 버튼 활성화/비활성화 |
| `notDeletableReason` | String | 삭제 불가 사유 | 툴팁 또는 안내 메시지 |
| `minRecruitmentCount` | Integer | 최소 모집 인원 | 인원 입력 필드 min값 설정 |

#### 수정 가능 여부

| 필드 | 타입 | 설명 | 용도 |
|------|------|------|------|
| `editable` | Boolean | 수정 가능 여부 | `false`면 폼 비활성화 |
| `notEditableReason` | String | 수정 불가 사유 | 안내 메시지 표시 |

---

## 2. 프로젝트 수정 API

### 요청

```
PUT /api/v1/projects/{projectId}
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

### 요청 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `request` | JSON (RequestPart) | O | 수정 데이터 (아래 참조) |
| `file` | MultipartFile | X | 새 커버 이미지 (변경 시에만) |

### request JSON 구조

```json
{
  "name": "수정된 프로젝트명",
  "description": "수정된 설명",
  "projectCategory": "IT_SERVICE",
  "platformCategory": "WEB",
  "githubRepositoryUrl": "https://github.com/...",
  "communicationChannelUrl": "https://discord.gg/...",
  "endDate": "2025-12-31",
  "recruitments": [
    {
      "recruitmentStateId": 1,
      "jobFieldCode": "BACKEND",
      "jobPositionCode": "JAVA_SPRING",
      "recruitmentCount": 4,
      "techStackIds": [30, 31, 38, 39]
    },
    {
      "recruitmentStateId": null,
      "jobFieldCode": "INFRA",
      "jobPositionCode": "DEVOPS",
      "recruitmentCount": 1,
      "techStackIds": [50, 51]
    }
  ],
  "confirmDeletePositionsWithPendingApplicants": false
}
```

### 수정 요청 필드 설명

#### 프로젝트 기본 정보

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `name` | String | O | 프로젝트명 |
| `description` | String | O | 프로젝트 설명 |
| `projectCategory` | String | O | 프로젝트 카테고리 코드 |
| `platformCategory` | String | O | 출시 플랫폼 코드 |
| `githubRepositoryUrl` | String | X | GitHub 레포 URL |
| `communicationChannelUrl` | String | X | 소통 채널 URL |
| `endDate` | String | O | 마감일 (YYYY-MM-DD) |

#### 모집 포지션 (`recruitments` 배열)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `recruitmentStateId` | Long | **조건부** | 기존 포지션: Pre-fill에서 받은 ID 그대로 전송<br>신규 포지션: `null` |
| `jobFieldCode` | String | O | 직군 코드 |
| `jobPositionCode` | String | O | 포지션 코드 |
| `recruitmentCount` | Integer | O | 모집 인원 (최소 1, 기존 포지션은 `minRecruitmentCount` 이상) |
| `techStackIds` | List\<Long\> | O | 기술 스택 ID 목록 (최소 1개) |

#### 특수 플래그

| 필드 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `confirmDeletePositionsWithPendingApplicants` | Boolean | false | 대기 지원자가 있는 포지션 삭제 확인 플래그 |

---

## 3. recruitmentStateId 사용 방법

### 핵심 개념

`recruitmentStateId`는 **기존 포지션 수정 vs 신규 포지션 추가**를 구분하는 핵심 필드입니다.

### 동작 방식

| recruitmentStateId 값 | 동작 |
|----------------------|------|
| Pre-fill에서 받은 ID (예: 1, 2, 3) | 해당 ID의 기존 포지션을 **수정** |
| `null` | **신규 포지션 추가** |
| 요청에 아예 없음 | 해당 포지션 **삭제** |

### 예시

**Pre-fill 응답:**
```json
"recruitments": [
  { "recruitmentStateId": 1, "jobPositionName": "Java/Spring", ... },
  { "recruitmentStateId": 2, "jobPositionName": "React", ... },
  { "recruitmentStateId": 3, "jobPositionName": "Kotlin/Spring", ... }
]
```

**수정 요청 (ID 2를 삭제하고, 신규 포지션 추가):**
```json
"recruitments": [
  { "recruitmentStateId": 1, ... },      // 기존 포지션 수정
  // recruitmentStateId: 2는 요청에 없음 → 삭제
  { "recruitmentStateId": 3, ... },      // 기존 포지션 수정
  { "recruitmentStateId": null, ... }    // 신규 포지션 추가
]
```

---

## 4. 포지션 삭제 처리 흐름

### Case 1: 삭제 가능한 포지션 (deletable: true, pendingApplicationCount: 0)

```
1. 사용자가 삭제 버튼 클릭
2. 요청에서 해당 포지션 제외
3. 수정 요청 전송 → 성공
```

### Case 2: 대기 지원자가 있는 포지션 (deletable: true, pendingApplicationCount > 0)

```
1. 사용자가 삭제 버튼 클릭
2. 확인 모달 표시:
   "해당 포지션의 대기 지원자 {n}명의 지원이 모두 거절됩니다. 정말 삭제하시겠습니까?"
3-A. 확인 클릭:
   - confirmDeletePositionsWithPendingApplicants: true 설정
   - 요청에서 해당 포지션 제외
   - 수정 요청 전송 → 성공 (대기 지원자 자동 거절)
3-B. 취소 클릭:
   - 삭제 취소, 원래 상태 유지
```

### Case 3: 삭제 불가능한 포지션 (deletable: false)

```
1. 삭제 버튼 비활성화 또는 숨김
2. 툴팁: notDeletableReason 표시
   예: "승인된 팀원이 있는 포지션은 삭제할 수 없습니다."
```

---

## 5. 모집 인원 수정 제약

### 최소 인원 제한

`minRecruitmentCount` 값보다 작게 설정할 수 없습니다.

```
예시:
- Pre-fill 응답: minRecruitmentCount: 2
- 인원 입력 필드: min="2" 설정
- 사용자가 1명으로 입력 시도 → 유효성 검사 실패
```

### UI 구현 예시

```html
<input
  type="number"
  min={recruitment.minRecruitmentCount}
  value={recruitment.recruitmentCount}
/>
<span class="hint">
  현재 {recruitment.currentCount}명 승인됨 (최소 {recruitment.minRecruitmentCount}명)
</span>
```

---

## 6. 에러 응답

### 권한 없음 (403)

```json
{
  "isSuccess": false,
  "code": "PROJECT_EDIT_FORBIDDEN",
  "message": "프로젝트 수정 권한이 없습니다."
}
```

### 모집 중단 상태에서 수정 시도 (400)

```json
{
  "isSuccess": false,
  "code": "PROJECT_EDIT_NOT_ALLOWED_SUSPENDED",
  "message": "모집이 중단된 상태에서는 수정할 수 없습니다."
}
```

### 모집 인원 축소 불가 (400)

```json
{
  "isSuccess": false,
  "code": "RECRUITMENT_COUNT_BELOW_CURRENT",
  "message": "현재 승인된 인원보다 적게 설정할 수 없습니다."
}
```

### 승인 인원이 있는 포지션 삭제 시도 (400)

```json
{
  "isSuccess": false,
  "code": "RECRUITMENT_HAS_APPROVED_MEMBERS",
  "message": "승인된 팀원이 있는 포지션은 삭제할 수 없습니다."
}
```

### 대기 지원자 확인 없이 삭제 시도 (400)

```json
{
  "isSuccess": false,
  "code": "RECRUITMENT_HAS_PENDING_APPLICANTS",
  "message": "대기중인 지원자가 있습니다. 삭제하려면 확인이 필요합니다."
}
```

→ 이 에러 발생 시, `confirmDeletePositionsWithPendingApplicants: true`로 재요청

---

## 7. 수정 성공 응답

```json
{
  "isSuccess": true,
  "code": "200",
  "data": {
    "projectId": 1,
    "name": "수정된 프로젝트명",
    "updatedAt": "2025-03-03T22:30:00",
    "autoRejectedCount": 3
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `projectId` | Long | 수정된 프로젝트 ID |
| `name` | String | 수정된 프로젝트명 |
| `updatedAt` | String | 수정 시각 |
| `autoRejectedCount` | Integer | 자동 거절된 지원자 수 (포지션 삭제로 인해) |

---

## 8. 프론트엔드 체크리스트

- [ ] Pre-fill API 호출하여 기존 데이터 로드
- [ ] `editable: false`일 때 폼 비활성화 및 안내 메시지 표시
- [ ] 각 포지션의 `deletable` 상태에 따라 삭제 버튼 활성화/비활성화
- [ ] 모집 인원 입력 필드에 `min={minRecruitmentCount}` 설정
- [ ] 삭제 시 `pendingApplicationCount > 0`이면 확인 모달 표시
- [ ] 수정 요청 시 `recruitmentStateId` 올바르게 매핑
  - 기존 포지션: Pre-fill에서 받은 ID
  - 신규 포지션: `null`
  - 삭제할 포지션: 요청에서 제외
- [ ] 이미지 변경 시에만 `file` 파라미터 포함
- [ ] 에러 응답에 따른 적절한 에러 메시지 표시

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-03-03 | 최초 작성 |