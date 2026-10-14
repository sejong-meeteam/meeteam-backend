# Job 구조 설계 문서

## 1. 개요

MeeTeam 플랫폼에서 사용하는 직군(JobField), 직무(JobPosition), 기술 스택(TechStack) 구조를 정의합니다.

회원가입 및 프로젝트 등록 시 동일한 구조를 사용합니다.

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

- `JobDataInitializer`에서 애플리케이션 시작 시 초기 데이터 생성
- 이미 존재하는 데이터는 중복 생성하지 않음 (findByCode/findByName 체크)

---

## 4. JobField (직군) 목록

| ID | Code | Name |
|----|------|------|
| 1 | PLANNING | 기획 |
| 2 | DESIGN | 디자인 |
| 3 | FRONTEND | 프론트 |
| 4 | BACKEND | 백엔드 |
| 5 | AI | AI |
| 6 | INFRA_OPERATION | 인프라/운영 |

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
| 클라우드 | AWS, EC2, RDS, S3, IAM |
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
1. 관심 JobField 선택 (복수 선택 가능)
   예: [백엔드, 프론트]

2. 선택한 JobField에 해당하는 TechStack 선택
   예: [Spring Boot, React.js, TypeScript]

3. 주력 JobPosition 선택
   예: Java/Spring
```

### 7.2 프로젝트 등록 시 (모집 분야)

```
1. 모집할 JobField 선택
   예: 백엔드

2. 해당 JobField의 JobPosition 선택
   예: Java/Spring

3. 필요한 TechStack 선택 (해당 JobField 범위 내)
   예: [Spring Boot, JPA, MySQL]

4. 모집 인원 입력
   예: 2명
```

---

## 8. API 응답 구조

`GET /api/jobs/options` 응답:

```json
{
  "fields": [
    {
      "id": 4,
      "code": "BACKEND",
      "name": "백엔드",
      "positions": [
        { "id": 10, "code": "JAVA_SPRING", "name": "Java/Spring" },
        { "id": 11, "code": "KOTLIN_SPRING", "name": "Kotlin/Spring" },
        { "id": 12, "code": "NODE_NESTJS", "name": "Node.js/NestJS" },
        { "id": 13, "code": "PYTHON_BACKEND", "name": "Python Backend" }
      ],
      "techStacks": [
        { "id": 20, "name": "Java" },
        { "id": 21, "name": "Spring Boot" },
        { "id": 22, "name": "JPA" }
        // ...
      ]
    }
    // ... 다른 fields
  ],
  "allPositions": [ /* 전체 Position 목록 */ ],
  "allTechStacks": [ /* 전체 TechStack 목록 */ ]
}
```

---

## 9. 향후 확장 고려

### 추가 예정 기술 스택 (우선순위별)

| 우선순위 | 항목 |
|---------|------|
| 높음 | Go, Terraform, GCP, Azure |
| 중간 | FastAPI, Svelte, GraphQL, Prisma |
| 낮음 | Rust, Blender, W&B |

### 추가 예정 JobPosition

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
