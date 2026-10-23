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

## 2. 기술 스택: 요청 ID와 응답 이름 구분

`GET /api/v1/jobs/options`의 `result.fields[].techStacks`에서 ID와 이름을 얻습니다. ID는 DB마다 달라질 수 있으므로 하드코딩하지 않습니다.

| API | 실제 요청 필드 |
|---|---|
| `POST /api/v1/auth/register/sejong` | multipart `request`의 `jobPositions: [{jobFieldCode, jobPositionCode, techStacks: [{id, displayOrder}]}]`, 선택 파일 `file` |
| `PUT /api/v1/members/me` | multipart `memberInfo`의 `jobPositionIds: number[]`, `techStacks: [{id, displayOrder}]`, 선택 파일 `profileImage` |
| `POST /api/v1/projects` | `recruitments: [{jobFieldCode, jobPositionCode, recruitmentCount, techStackIds: number[]}]` |
| `PUT /api/v1/projects/{projectId}` | 위 모집분야 형태에 기존 항목의 `recruitmentStateId` 포함 (신규는 null) |
| `POST /api/v1/projects/{projectId}/application` | `{jobPositionCode, motivation}` (기술 배열 없음) |

`displayOrder`는 1 이상입니다. 회원 상세·내 정보 응답의 `skills`는 기술 **이름**의 평면 문자열 배열이며 요청에 그대로 쓰는 문자열 배열이 아닙니다. 사용되지 않는 과거 DTO의 `skills` 필드를 현재 컨트롤러 계약으로 간주하지 않습니다.

### SQL seed의 기술 이름 (75개)

`Notion`, `Jira`, `Figma`, `Google Analytics`, `Zeplin`, `After Effects`, `Premiere`, `Illustrator`, `Photoshop`, `React.js`, `TypeScript`, `Next.js`, `Vue.js`, `Angular`, `Zustand`, `Redux`, `TailwindCSS`, `Swift`, `SwiftUI`, `UIKit`, `Firebase`, `Kotlin`, `Jetpack Compose`, `Coroutine`, `Hilt`, `React Native`, `Expo`, `Dart`, `Flutter`, `Java`, `Python`, `Ruby on Rails`, `Spring Boot`, `Spring WebFlux`, `NestJs`, `Node.js`, `JPA`, `QueryDSL`, `MySQL`, `PostgreSQL`, `MariaDB`, `MongoDB`, `Redis`, `ElasticSearch`, `Docker`, `AWS`, `Nginx`, `Linux`, `GitHub Actions`, `GitLab CI/CD`, `Jenkins`, `Prometheus`, `Grafana`, `WebSocket`, `Kafka`, `RabbitMQ`, `NumPy`, `Pandas`, `Scikit-learn`, `PyTorch`, `TensorFlow`, `CUDA`, `Hugging Face`, `OpenAI API`, `LangChain`, `RAG`, `Vector DB`, `MLflow`, `Airflow`, `Kubernetes`, `Prometheus/Grafana`, `Postman`, `Playwright`, `Selenium`, `Cypress`

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

## 5. PlatformCategory (플랫폼)

`IOS`, `ANDROID`, `WEB`.

## 6. ApplicationStatus와 지원 결정

| 상태 값 | 설명 |
|---|---|
| `PENDING` | 대기중 |
| `ACCEPTED` | 승인됨 |
| `REJECTED` | 거절됨 |
| `CANCELLED` | 취소됨 |

승인/거절 요청 필드는 `applicationStatus`가 아니라 `decision`이며, 의도된 요청은 `{ "decision": "ACCEPTED" }` 또는 `REJECTED`입니다. 취소는 `DELETE /api/v1/members/me/applications/{applicationId}`를 사용합니다.

**구현상 주의:** `ApplicationDecisionRequest`는 enum 전체를 타입으로 받고 `@NotNull`만 검증합니다. `ProjectApplicationServiceImpl.processDecision`도 ACCEPTED 외에는 거절 처리 경로로 들어가므로 PENDING/CANCELLED를 서버가 차단한다고 보장할 수 없습니다. 별도 구현 검토가 필요한 항목이며 프론트는 승인·거절 두 값만 보내야 합니다.

## 7. JobFieldCode (직군)

`PLANNING`, `DESIGN`, `FRONTEND`, `BACKEND`, `AI`, `INFRA_OPERATION` (6개).

- 프로젝트 검색 `GET /api/v1/projects/search`는 `jobField` enum 필터입니다.
- 회원 검색 `GET /api/v1/members/search`는 `jobFieldId` (Long), `techStackNames` (문자열 목록), `name`을 받습니다. `jobField` enum 필터가 아닙니다.
- 현재 jobs/options의 직군 객체에는 `code`, `name`만 있고 직군 ID는 없습니다. 회원 검색용 ID 확보 방법은 별도 계약 확인이 필요합니다. 직군 ID 1~6을 가정하지 않습니다.

## 요약

| 대상 | 타입 / 개수 |
|---|---|
| 직무 코드 | JobPositionCode 21개 (프로필 수정은 ID 배열) |
| 기술 | seed 75개, 요청은 ID / 표시 순서 객체 또는 ID 배열 |
| 성별 | 2개 |
| 프로젝트 카테고리 | 4개 |
| 플랫폼 | 3개 |
| 지원 상태 | 4개, 결정 요청 의도는 2개 |
| 직군 코드 | 6개, 회원 검색은 직군 ID |

## 코드 근거

- `domain/job/entity/JobFieldCode.java`, `JobPositionCode.java`, `domain/project/entity/ProjectCategory.java`, `domain/application/entity/ApplicationStatus.java`
- `web/auth/dto/sejong/SejongRegisterRequest.java`, `web/auth/dto/register/MemberJobPositionRequest.java`, `TechStackOrderRequest.java`
- `web/member/dto/request/MemberProfileUpdateRequest.java`, `web/member/controller/MemberController.java`
- `web/project/dto/request/ProjectPostRequest.java`, `ProjectEditRequest.java`, `ProjectRecruitRequest.java`, `RecruitmentEditRequest.java`
- `web/job/dto/response/JobOptionResponse.java`, `JobFieldOptionResponse.java` (Java 경로 기준 루트: `src/main/java/com/wardk/meeteam_backend/`)
