> 기준: 공개 코드 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`의 enum·DTO·SQL seed. 테스트 및 운영 DB 적용 여부는 검증하지 않았습니다.

# Job 구조 설계 문서

## 1. 개요

MeeTeam 플랫폼에서 사용하는 직군(JobField), 직무(JobPosition), 기술 스택(TechStack) 구조를 정의합니다.

회원가입과 프로젝트 등록은 같은 직군·직무·기술 마스터를 참조하지만 요청 DTO는 다릅니다. 회원가입은 기술 ID/표시 순서 객체, 프로젝트 모집은 기술 ID 배열을 사용합니다.

---

## 2. 엔티티 관계도

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   ┌──────────────┐         ┌──────────────────────┐             │
│   │   JobField   │ 1     N │    JobPosition       │             │
│   │   (직군)     │─────────│    (직무)            │             │
│   │              │         │                      │             │
│   │ - id         │         │ - id                 │             │
│   │ - code       │         │ - jobField (FK)      │             │
│   │ - name       │         │ - code               │             │
│   └──────┬───────┘         │ - name               │             │
│          │                 └──────────────────────┘             │
│          │                                                      │
│          │ 1                                                    │
│          │                                                      │
│          │         ┌──────────────────────┐                     │
│          │       N │ JobFieldTechStack    │ N                   │
│          └─────────│ (연결 테이블)        │─────────┐           │
│                    │                      │         │           │
│                    │ - id                 │         │ 1         │
│                    │ - jobField (FK)      │         │           │
│                    │ - techStack (FK)     │   ┌─────┴────────┐  │
│                    └──────────────────────┘   │  TechStack   │  │
│                                               │  (기술 스택) │  │
│                                               │              │  │
│                                               │ - id         │  │
│                                               │ - name       │  │
│                                               └──────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 관계 요약

| 관계 | 설명 |
|------|------|
| JobField : JobPosition | 1 : N (하나의 직군에 여러 직무) |
| JobField : TechStack | N : M (다대다, JobFieldTechStack으로 연결) |

---

## 3. 설계 전략

### 3.1 느슨한 연결 (Loose Coupling)

**의도적으로 TechStack을 JobField 단위로 연결**

```
JobField (프론트) ──── TechStack (React.js, Swift, Flutter 등 모두 포함)
       │
       ├── JobPosition (웹 프론트엔드)
       ├── JobPosition (iOS)
       ├── JobPosition (Android)
       └── JobPosition (크로스 플랫폼)
```

**장점:**
- 유연한 기술 스택 선택 가능
- 풀스택, 하이브리드 개발자 수용
- 새로운 기술 추가 시 관리 용이

**트레이드오프:**
- iOS 개발자가 React.js 선택 가능 (의도된 유연성)

### 3.2 데이터 초기화 전략

- 초기 데이터 정의는 `src/main/resources/db/seed/job-seed.sql`입니다 (직군 6개, 직무 21개, 기술 75개).
- 기준 커밋에 `JobDataInitializer` 클래스는 없습니다. 앱 시작 시 자동 생성된다고 단정하지 않습니다.
- SQL은 `ON DUPLICATE KEY UPDATE`를 사용합니다. 실제 적용 여부 및 기존 DB의 키·데이터 상태는 별도 확인이 필요합니다.

---

## 4. JobField (직군) 목록

| Code | Name |
|------|------|
| PLANNING | 기획 |
| DESIGN | 디자인 |
| FRONTEND | 프론트 |
| BACKEND | 백엔드 |
| AI | AI |
| INFRA_OPERATION | 인프라/운영 |

---

## 5. JobPosition (직무) 목록

### 5.1 기획 (PLANNING)

| Code | Name |
|------|------|
| PRODUCT_MANAGER | PM 프로덕트 매니저 |
| PRODUCT_OWNER | PO 프로덕트 오너 |
| SERVICE_PLANNER | 서비스 기획 |

### 5.2 디자인 (DESIGN)

| Code | Name |
|------|------|
| UI_UX_DESIGNER | UI/UX 디자이너 |
| MOTION_DESIGNER | 모션 디자이너 |
| BX_BRAND_DESIGNER | BX 브랜드 디자이너 |

### 5.3 프론트 (FRONTEND)

| Code | Name |
|------|------|
| WEB_FRONTEND | 웹 프론트엔드 |
| IOS | iOS |
| ANDROID | Android |
| CROSS_PLATFORM | 크로스 플랫폼 |

### 5.4 백엔드 (BACKEND)

| Code | Name |
|------|------|
| JAVA_SPRING | Java/Spring |
| KOTLIN_SPRING | Kotlin/Spring |
| NODE_NESTJS | Node.js/NestJS |
| PYTHON_BACKEND | Python Backend |

### 5.5 AI

| Code | Name |
|------|------|
| MACHINE_LEARNING | 머신 러닝 |
| DEEP_LEARNING | 딥러닝 |
| LLM | LLM |
| MLOPS | MLOps |

### 5.6 인프라/운영 (INFRA_OPERATION)

| Code | Name |
|------|------|
| DEVOPS_ARCHITECT | DevOps 엔지니어/아키텍처 |
| QA | QA |
| CLOUD_ENGINEER | Cloud 엔지니어 |

---

## 6. TechStack (기술 스택) 목록

### 6.1 기획

| 기술 스택 |
|----------|
| Notion |
| Jira |
| Figma |
| Google Analytics |

### 6.2 디자인

| 기술 스택 |
|----------|
| Figma |
| Zeplin |
| After Effects |
| Premiere |
| Illustrator |
| Photoshop |

### 6.3 프론트

| 분류 | 기술 스택 |
|------|----------|
| 웹 | React.js, TypeScript, Next.js, Vue.js, Angular, Zustand, Redux, TailwindCSS |
| iOS | Swift, SwiftUI, UIKit, Firebase |
| Android | Kotlin, Jetpack Compose, Coroutine, Hilt |
| 크로스플랫폼 | React Native, Expo, Dart, Flutter |

### 6.4 백엔드

| 분류 | 기술 스택 |
|------|----------|
| 언어 | Java, Kotlin, TypeScript, Python, Ruby on Rails |
| 프레임워크 | Spring Boot, Spring WebFlux, NestJs, Node.js |
| ORM/쿼리 | JPA, QueryDSL |
| 데이터베이스 | MySQL, PostgreSQL, MariaDB, MongoDB, Redis, ElasticSearch |
| 인프라 | Docker, AWS, Nginx, Linux |
| CI/CD | GitHub Actions, GitLab CI/CD, Jenkins |
| 모니터링 | Prometheus, Grafana |
| 메시징 | WebSocket, Kafka, RabbitMQ |

### 6.5 AI

| 분류 | 기술 스택 |
|------|----------|
| 언어 | Python |
| 데이터 처리 | NumPy, Pandas |
| ML/DL | Scikit-learn, PyTorch, TensorFlow, CUDA |
| LLM | Hugging Face, OpenAI API, LangChain, RAG, Vector DB |
| MLOps | MLflow, Airflow |
| 인프라 | Docker, Kubernetes, AWS |

### 6.6 인프라/운영

| 분류 | 기술 스택 |
|------|----------|
| 클라우드 | AWS |
| 컨테이너 | Docker, Kubernetes |
| 웹서버 | Nginx |
| CI/CD | GitHub Actions |
| 모니터링 | Prometheus/Grafana |
| 테스팅 | Postman, Playwright, Selenium, Cypress |
| 협업 | Jira |

---

## 7. 사용자 선택 흐름

### 7.1 회원가입 시

```
1. 관심 JobField와 JobPosition 선택 (jobPositions 목록)
   예: [백엔드, 프론트]

2. 선택한 JobField의 TechStack을 {id, displayOrder}로 선택
   예: [Spring Boot, React.js, TypeScript]

3. jobPositions 목록 순서를 구성
   예: 첫 직무 Java/Spring (프로필 대표 포지션은 첫 번째 직무에서 계산)
```

### 7.2 프로젝트 등록 시 (모집 분야)

```
1. 모집할 JobField 선택
   예: 백엔드

2. 해당 JobField의 JobPosition 선택
   예: Java/Spring

3. 필요한 TechStack ID 목록 선택 (해당 JobField 옵션을 기준으로 구성)
   예: [Spring Boot, JPA, MySQL]

4. 모집 인원 입력
   예: 2명
```

---

## 8. API 응답 구조

`GET /api/v1/jobs/options`는 `SuccessResponse<JobOptionResponse>`를 반환합니다. 아래는 `result` 내부만 발췌한 예시입니다. 숫자 ID는 설명용이며 실제 응답값을 사용해야 합니다.

```json
{
  "fields": [
    {
      "code": "BACKEND",
      "name": "백엔드",
      "positions": [
        { "id": 11, "code": "JAVA_SPRING", "name": "Java/Spring" }
      ],
      "techStacks": [
        { "id": 30, "name": "Java" }
      ]
    }
  ]
}
```

응답 최상위는 `code`, `message`, `result`입니다. `result`에는 `fields`만 있고 `allPositions`/`allTechStacks`는 없습니다. 직군 객체에는 `id`가 없고 직무·기술 객체에는 `id`가 있습니다. 직무와 기술은 ID 오름차순으로 정렬됩니다.

근거: `web/job/controller/JobController.java:20-22`, `web/job/dto/response/JobOptionResponse.java:9-13`, `JobFieldOptionResponse.java:11-15`, `domain/job/service/JobInfoServiceImpl.java:39-51` (루트: `src/main/java/com/wardk/meeteam_backend/`).

---

## 9. 향후 확장 고려

아래 항목은 설계 제안이며 기준 커밋의 enum/seed에 반영된 구현이나 확정 일정이 아닙니다.

### 추가 고려 기술 스택 (제안 우선순위)

| 우선순위 | 항목 |
|---------|------|
| 높음 | Go, Terraform, GCP, Azure |
| 중간 | FastAPI, Svelte, GraphQL, Prisma |
| 낮음 | Rust, Blender, W&B |

### 추가 고려 JobPosition

| JobField | 추가 고려 Position |
|----------|-------------------|
| 기획 | 그로스 해커, 비즈니스 기획 |
| 디자인 | 그래픽 디자이너, 일러스트레이터 |
| 백엔드 | Go Backend |
| AI | 데이터 엔지니어, 데이터 사이언티스트 |
| 인프라 | SRE, 보안 엔지니어 |

---

## 10. 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-02-15 | 최초 문서 작성 |
| 2026-02-15 | Node/NextJS → Node.js/NestJS 명칭 변경 |


## 데이터 기준과 사용 시 주의

- 이 목록은 `src/main/resources/db/seed/job-seed.sql`의 직군별 매핑입니다. 기술은 직군끼리 중복될 수 있으며 고유 기술 seed는 75개입니다.
- 직군·직무 코드는 `domain/job/entity/JobFieldCode.java`, `JobPositionCode.java`와 일치합니다 (Java 루트: `src/main/java/com/wardk/meeteam_backend/`).
- `GET /api/v1/jobs/options`의 현재 응답에서 직무·기술 ID를 조회합니다. DB ID를 고정값으로 가정하지 않습니다. seed 파일 존재는 운영 DB 적용 또는 시작 시 자동 실행의 증거가 아닙니다.
- 요청 형식과 회원 검색의 직군 ID 제약은 [프론트엔드 API 값 가이드](../api/FRONTEND_API_VALUES.md)를 참고합니다.
