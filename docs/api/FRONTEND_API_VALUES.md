# 프론트엔드 API 요청 고정값 가이드

> 기준: 공개 코드 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`. 정적 코드·SQL 대조이며 배포 DB 데이터와 테스트 통과는 확인하지 않았습니다.

## 1. JobPositionCode (직무 코드)

프로젝트 생성의 `creatorJobPositionCode`, 수정의 `leaderJobPositionCode`, 모집분야·회원가입의 `jobPositionCode`, 지원의 `jobPositionCode`에 사용합니다. 프로필 수정은 코드 배열이 아니라 `jobPositionIds: number[]`입니다.

| 코드 | 직군 | 이름 |
|---|---|---|
| `PRODUCT_MANAGER` | `PLANNING` | PM 프로덕트 매니저 |
| `PRODUCT_OWNER` | `PLANNING` | PO 프로덕트 오너 |
| `SERVICE_PLANNER` | `PLANNING` | 서비스 기획 |
| `UI_UX_DESIGNER` | `DESIGN` | UI/UX 디자이너 |
| `MOTION_DESIGNER` | `DESIGN` | 모션 디자이너 |
| `BX_BRAND_DESIGNER` | `DESIGN` | BX 브랜드 디자이너 |
| `WEB_FRONTEND` | `FRONTEND` | 웹 프론트엔드 |
| `IOS` | `FRONTEND` | iOS |
| `ANDROID` | `FRONTEND` | Android |
| `CROSS_PLATFORM` | `FRONTEND` | 크로스 플랫폼 |
| `JAVA_SPRING` | `BACKEND` | Java/Spring |
| `KOTLIN_SPRING` | `BACKEND` | Kotlin/Spring |
| `NODE_NESTJS` | `BACKEND` | Node.js/NestJS |
| `PYTHON_BACKEND` | `BACKEND` | Python Backend |
| `MACHINE_LEARNING` | `AI` | 머신 러닝 |
| `DEEP_LEARNING` | `AI` | 딥러닝 |
| `LLM` | `AI` | LLM |
| `MLOPS` | `AI` | MLOps |
| `DEVOPS_ARCHITECT` | `INFRA_OPERATION` | DevOps 엔지니어/아키텍처 |
| `QA` | `INFRA_OPERATION` | QA |
| `CLOUD_ENGINEER` | `INFRA_OPERATION` | Cloud 엔지니어 |
| `PERFORMANCE_MARKETER` | `MARKETING` | 퍼포먼스 마케터 |
| `CONTENT_MARKETER` | `MARKETING` | 콘텐츠 마케터 |
| `BRAND_MARKETER` | `MARKETING` | 브랜드 마케터 |
| `DATA_ANALYST` | `DATA` | 데이터 분석가 |
| `DATA_ENGINEER` | `DATA` | 데이터 엔지니어 |
| `DATA_SCIENTIST` | `DATA` | 데이터 사이언티스트 |

## 2. 기술 스택: 요청 ID와 응답 이름 구분

`GET /api/v1/jobs/options`의 `result.fields[].techStacks`에서 ID와 이름을 얻습니다. ID는 DB마다 달라질 수 있으므로 하드코딩하지 않습니다.

| API | 실제 요청 필드 |
|---|---|
| `POST /api/v1/auth/register/sejong` | multipart `request`의 `jobPositions: [{jobFieldCode, jobPositionCode, techStacks: [{id, displayOrder}]}]`, 선택 파일 `file` |
| `PUT /api/v1/members/me` | multipart `memberInfo`의 `jobPositionIds: number[]`, `techStacks: [{id, displayOrder}]`, 선택 파일 `profileImage` |
| `POST /api/v1/projects` | `recruitments: [{jobFieldCode, jobPositionCode, recruitmentCount, techStackIds: number[]}]` |
| `PUT /api/v1/projects/{projectId}` | 위 모집분야 형태에 기존 항목의 `recruitmentStateId` 포함 (신규는 null) |
| `POST /api/v1/projects/{projectId}/application` | `{jobPositionCode, motivation}` (기술 배열 없음). `motivation`은 공백이 아닌 10~700자 |

`displayOrder`는 1 이상입니다. 회원 상세·내 정보 응답의 `skills`는 기술 **이름**의 평면 문자열 배열이며 요청에 그대로 쓰는 문자열 배열이 아닙니다. 사용되지 않는 과거 DTO의 `skills` 필드를 현재 컨트롤러 계약으로 간주하지 않습니다.

### SQL seed의 기술 이름 (82개)

`Notion`, `Jira`, `Figma`, `Google Analytics`, `Zeplin`, `After Effects`, `Premiere`, `Illustrator`, `Photoshop`, `React.js`, `TypeScript`, `Next.js`, `Vue.js`, `Angular`, `Zustand`, `Redux`, `TailwindCSS`, `Swift`, `SwiftUI`, `UIKit`, `Firebase`, `Kotlin`, `Jetpack Compose`, `Coroutine`, `Hilt`, `React Native`, `Expo`, `Dart`, `Flutter`, `Java`, `Python`, `Ruby on Rails`, `Spring Boot`, `Spring WebFlux`, `NestJs`, `Node.js`, `JPA`, `QueryDSL`, `MySQL`, `PostgreSQL`, `MariaDB`, `MongoDB`, `Redis`, `ElasticSearch`, `Docker`, `AWS`, `Nginx`, `Linux`, `GitHub Actions`, `GitLab CI/CD`, `Jenkins`, `Prometheus`, `Grafana`, `WebSocket`, `Kafka`, `RabbitMQ`, `NumPy`, `Pandas`, `Scikit-learn`, `PyTorch`, `TensorFlow`, `CUDA`, `Hugging Face`, `OpenAI API`, `LangChain`, `RAG`, `Vector DB`, `MLflow`, `Airflow`, `Kubernetes`, `Prometheus/Grafana`, `Postman`, `Playwright`, `Selenium`, `Cypress`, `Meta Ads`, `Google Ads`, `Canva`, `R`, `SQL`, `Tableau`, `Excel`

마케팅 직군의 GA4는 별도 이름 없이 기존 `Google Analytics`를 사용합니다.

출처: `src/main/resources/db/seed/job-seed.sql`. 이 목록은 SQL에 정의된 초기값이며 운영 DB의 현재 전체 목록/자동 초기화 실행을 보장하지 않습니다. 직군별 매핑은 [직군·기술 스택](../job-tech-stack.md)을 참고합니다. 철자도 seed 그대로 사용합니다 (`TailwindCSS`, `ElasticSearch`, `NestJs`, `AWS`, `Kafka`).

## 3. Gender (성별)

회원가입·프로필 수정: `MALE`(남성), `FEMALE`(여성).

## 4. ProjectCategory (프로젝트 카테고리)

프로젝트 생성·수정·검색에 사용합니다.

| 값 | 설명 |
|---|---|
| `CAPSTONE` | 캡스톤 |
| `CREATIVE_SEMESTER` | 창의학기제 |
| `CLUB` | 동아리 |
| `ETC` | 기타 |
| `STUDY` | 스터디 |
| `CONTEST` | 공모전 |

`STUDY`·`CONTEST`는 `V9__ut_round1_project_job_application.sql` 적용 후 저장할 수 있습니다. 기존 값의 코드와 순서는 바뀌지 않았습니다.

## 5. PlatformCategory (플랫폼)

`IOS`, `ANDROID`, `WEB`.

## 6. ApplicationStatus와 지원 결정

| 상태 값 | 설명 |
|---|---|
| `PENDING` | 대기중 |
| `ACCEPTED` | 승인됨 |
| `REJECTED` | 거절됨 |
| `CANCELLED` | 취소됨 |

지원 요청의 `motivation`은 필수이며 10자 이상 700자 이하입니다. 공백만 보내거나 길이를 벗어나면 `400` / `COMMON400`과 "지원 동기를 입력해 주세요." 또는 "지원 동기는 10자 이상 700자 이하로 입력해 주세요."를 돌려줍니다. 길이는 Java 문자열 길이(UTF-16) 기준이라 한글·영문은 한 글자를 1로 셉니다.

승인/거절 요청 필드는 `applicationStatus`가 아니라 `decision`이며, 의도된 요청은 `{ "decision": "ACCEPTED" }` 또는 `REJECTED`입니다. 취소는 `DELETE /api/v1/members/me/applications/{applicationId}`를 사용합니다.

`PENDING`·`CANCELLED`를 `decision`으로 보내면 잠금·저장 없이 `400` / `COMMON400`을 돌려줍니다.

### 지원자 목록 `status` 필터와 `decidedAt`

`GET /api/v1/projects/{projectId}/applications?status=ACCEPTED`처럼 리더가 처리한 지원자를 다시 볼 수 있습니다.

| `status` | 반환 대상 | 정렬 |
|---|---|---|
| 생략 또는 `PENDING` | 대기 지원자 (지금과 같음) | 지원 시각 최신순 |
| `ACCEPTED` | 승인한 지원자 | 처리 시각 최신순 |
| `REJECTED` | 거절한 지원자 (포지션 삭제로 자동 거절된 지원자 포함) | 처리 시각 최신순 |

`CANCELLED`나 그 밖의 값은 `400`입니다. 응답 항목에는 `decidedAt`(승인·거절 시각, ISO-8601 `LocalDateTime`)이 추가됐고 `PENDING`이면 `null`입니다. V9 이전에 처리된 지원서는 마지막 수정 시각으로 채웠습니다. 지원한 포지션이 이후 삭제된 거절 지원자는 `currentCount`, `recruitmentCount`, `isRecruitmentFull`이 `null`입니다. `status` 필드는 지금처럼 표시 이름("대기중", "승인됨", "거절됨")입니다.

## 7. JobFieldCode (직군)

`PLANNING`, `DESIGN`, `FRONTEND`, `BACKEND`, `AI`, `INFRA_OPERATION`, `MARKETING`(마케팅), `DATA`(데이터) (8개). `MARKETING`·`DATA`는 V9 적용 후 `GET /api/v1/jobs/options`에 자동으로 포함됩니다. 화면에 직군 코드를 하드코딩했다면 두 값을 추가해야 합니다.

- 프로젝트 검색 `GET /api/v1/projects/search`는 `jobField` enum 필터입니다. 회의 방식 필터 `meetingMode`는 8절을 참고합니다.
- 회원 검색 `GET /api/v1/members/search`는 `jobFieldId` (Long), `techStackNames` (문자열 목록), `name`을 받습니다. `jobField` enum 필터가 아닙니다.
- 현재 jobs/options의 직군 객체에는 `code`, `name`만 있고 직군 ID는 없습니다. 회원 검색용 ID 확보 방법은 별도 계약 확인이 필요합니다. 직군 ID를 고정값으로 가정하지 않습니다.

## 8. 프로젝트 협업 방식 (선택 항목)

지원 전에 회의 방식과 시간 부담을 보여 주기 위한 값입니다. 세 필드 모두 선택이며, 입력하지 않았거나 V9 이전에 만든 프로젝트는 `null`입니다.

| 필드 | 타입 | 규칙 | 표시 예 |
|---|---|---|---|
| `meetingMode` | `ONLINE` / `OFFLINE` / `HYBRID` | 생략 가능 | 온라인 / 대면 / 온·오프라인 병행 |
| `weeklyHours` | 정수 | 1~40, 생략 가능 | "주 N시간 예상" |
| `meetingNote` | 문자열 | 100자 이하, 생략 가능. 공백만 보내면 `null`로 저장 | "화요일 저녁 7시 디스코드" |

| API | 위치 |
|---|---|
| `POST /api/v1/projects` | multipart `request` JSON에 세 필드 추가 |
| `PUT /api/v1/projects/{projectId}` | multipart `request` JSON에 세 필드 추가. **보낸 값으로 교체**하므로 생략하면 `null`로 지워집니다. 수정 화면은 prefill 값을 다시 보내야 합니다 |
| `GET /api/v1/projects/{projectId}/edit` (수정 prefill) | 세 필드 포함 |
| `GET /api/v1/projects/{projectId}` 상세 응답 | 세 필드 포함 |
| 프로젝트 카드 (`/api/v1/projects/search`, 메인 페이지 등 `ProjectCardResponse`) | `meetingMode`, `weeklyHours`만 포함 |

### 검색 필터 `meetingMode`

`GET /api/v1/projects/search?meetingMode=ONLINE`처럼 단일 값으로 보냅니다. 생략하면 지금처럼 회의 방식과 관계없이 모두 반환합니다.

| 요청 값 | 반환 대상 |
|---|---|
| `ONLINE` | `ONLINE`과 `HYBRID` ("온라인 가능" 필터) |
| `OFFLINE` | `OFFLINE`만 |
| `HYBRID` | `HYBRID`만 |

회의 방식을 입력하지 않은 프로젝트(`null`)는 필터를 보낼 때 결과에 포함되지 않습니다.

범위를 벗어나면 기존 검증 오류와 같은 `400` / `COMMON400` 응답과 다음 메시지를 돌려줍니다: "주 예상 활동 시간은 1~40시간으로 입력해 주세요.", "협업 방식 메모는 최대 100자까지 입력 가능합니다."

## 요약

| 대상 | 타입 / 개수 |
|---|---|
| 직무 코드 | JobPositionCode 27개 (프로필 수정은 ID 배열) |
| 기술 | seed 82개, 요청은 ID / 표시 순서 객체 또는 ID 배열 |
| 성별 | 2개 |
| 프로젝트 카테고리 | 6개 |
| 플랫폼 | 3개 |
| 지원 상태 | 4개, 결정 요청은 2개만 허용, 목록 필터는 3개 |
| 직군 코드 | 8개, 회원 검색은 직군 ID |
| 회의 방식 | 3개, 모두 선택 항목 |

## 코드 근거

- `domain/job/entity/JobFieldCode.java`, `JobPositionCode.java`, `domain/project/entity/ProjectCategory.java`, `MeetingMode.java`, `ProjectCollaboration.java`, `domain/application/entity/ApplicationStatus.java`
- `web/auth/dto/sejong/SejongRegisterRequest.java`, `web/auth/dto/register/MemberJobPositionRequest.java`, `TechStackOrderRequest.java`
- `web/member/dto/request/MemberProfileUpdateRequest.java`, `web/member/controller/MemberController.java`
- `web/project/dto/request/ProjectPostRequest.java`, `ProjectEditRequest.java`, `ProjectRecruitRequest.java`, `RecruitmentEditRequest.java`
- `web/application/controller/ApplicationController.java`, `web/application/dto/request/ApplicationRequest.java`, `web/application/dto/response/ProjectApplicationListResponse.java`
- `web/job/dto/response/JobOptionResponse.java`, `JobFieldOptionResponse.java` (Java 경로 기준 루트: `src/main/java/com/wardk/meeteam_backend/`)
