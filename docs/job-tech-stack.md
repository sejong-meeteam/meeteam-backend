> 기준: 공개 코드 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`의 enum·DTO·SQL seed. 테스트 및 운영 DB 적용 여부는 검증하지 않았습니다.

# JobField, JobPosition, TechStack 문서

MeeTeam 플랫폼에서 사용되는 직군(JobField), 직무(JobPosition), 기술 스택(TechStack) 정보입니다.

---

## 1. 기획 (PLANNING)

### 직무 (JobPosition)
| 코드 | 이름 |
|------|------|
| `PRODUCT_MANAGER` | PM 프로덕트 매니저 |
| `PRODUCT_OWNER` | PO 프로덕트 오너 |
| `SERVICE_PLANNER` | 서비스 기획 |

### 기술 스택 (TechStack)
Notion, Jira, Figma, Google Analytics

---

## 2. 디자인 (DESIGN)

### 직무 (JobPosition)
| 코드 | 이름 |
|------|------|
| `UI_UX_DESIGNER` | UI/UX 디자이너 |
| `MOTION_DESIGNER` | 모션 디자이너 |
| `BX_BRAND_DESIGNER` | BX 브랜드 디자이너 |

### 기술 스택 (TechStack)
- Figma
- Zeplin
- After Effects
- Premiere
- Illustrator
- Photoshop

---

## 3. 프론트 (FRONTEND)

### 직무 (JobPosition)
| 코드 | 이름 |
|------|------|
| `WEB_FRONTEND` | 웹 프론트엔드 |
| `IOS` | iOS |
| `ANDROID` | Android |
| `CROSS_PLATFORM` | 크로스 플랫폼 |

### 기술 스택 (TechStack)
React.js, TypeScript, Next.js, Vue.js, Angular, Zustand, Redux, TailwindCSS, Swift, SwiftUI, UIKit, Firebase, Kotlin, Jetpack Compose, Coroutine, Hilt, React Native, Expo, Dart, Flutter

---

## 4. 백엔드 (BACKEND)

### 직무 (JobPosition)
| 코드 | 이름 |
|------|------|
| `JAVA_SPRING` | Java/Spring |
| `KOTLIN_SPRING` | Kotlin/Spring |
| `NODE_NESTJS` | Node.js/NestJS |
| `PYTHON_BACKEND` | Python Backend |

### 기술 스택 (TechStack)
Java, Kotlin, TypeScript, Python, Ruby on Rails, Spring Boot, Spring WebFlux, NestJs, Node.js, JPA, QueryDSL, MySQL, PostgreSQL, MariaDB, MongoDB, Redis, ElasticSearch, Docker, AWS, Nginx, Linux, GitHub Actions, GitLab CI/CD, Jenkins, Prometheus, Grafana, WebSocket, Kafka, RabbitMQ

---

## 5. AI (AI)

### 직무 (JobPosition)
| 코드 | 이름 |
|------|------|
| `MACHINE_LEARNING` | 머신 러닝 |
| `DEEP_LEARNING` | 딥러닝 |
| `LLM` | LLM |
| `MLOPS` | MLOps |

### 기술 스택 (TechStack)
Python, NumPy, Pandas, Scikit-learn, PyTorch, TensorFlow, CUDA, Hugging Face, OpenAI API, LangChain, RAG, Vector DB, MLflow, Airflow, Docker, Kubernetes, AWS

---

## 6. 인프라/운영 (INFRA_OPERATION)

### 직무 (JobPosition)
| 코드 | 이름 |
|------|------|
| `DEVOPS_ARCHITECT` | DevOps 엔지니어/아키텍처 |
| `QA` | QA |
| `CLOUD_ENGINEER` | Cloud 엔지니어 |

### 기술 스택 (TechStack)
AWS, Docker, Kubernetes, Nginx, GitHub Actions, Prometheus/Grafana, Postman, Jira, Playwright, Selenium, Cypress

## 데이터 기준과 사용 시 주의

- 이 목록은 `src/main/resources/db/seed/job-seed.sql`의 직군별 매핑입니다. 기술은 직군끼리 중복될 수 있으며 고유 기술 seed는 75개입니다.
- 직군·직무 코드는 `domain/job/entity/JobFieldCode.java`, `JobPositionCode.java`와 일치합니다 (Java 루트: `src/main/java/com/wardk/meeteam_backend/`).
- `GET /api/v1/jobs/options`의 현재 응답에서 직무·기술 ID를 조회합니다. DB ID를 고정값으로 가정하지 않습니다. seed 파일 존재는 운영 DB 적용 또는 시작 시 자동 실행의 증거가 아닙니다.
- 요청 형식과 회원 검색의 직군 ID 제약은 [프론트엔드 API 값 가이드](api/FRONTEND_API_VALUES.md)를 참고합니다.
