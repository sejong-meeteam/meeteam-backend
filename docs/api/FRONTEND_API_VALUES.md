# 프론트엔드 API 요청 고정값 가이드

## 1. JobPosition (직무 포지션)

회원가입, 프로필 수정, 프로젝트 생성/모집분야, 지원 시 사용

| 값 | 설명 |
|---|---|
| `WEB_FRONTEND` | 웹프론트엔드 |
| `IOS` | iOS |
| `ANDROID` | 안드로이드 |
| `CROSS_PLATFORM` | 크로스플랫폼 |
| `WEB_SERVER` | 웹서버 |
| `AI` | AI |
| `PRODUCT_MANAGER` | 프로덕트 매니저/오너 |
| `GRAPHIC_DESIGN` | 그래픽디자인 |
| `UI_UX_DESIGN` | UI/UX디자인 |
| `MOTION_DESIGN` | 모션 디자인 |
| `ETC` | 기타 |

---

## 2. Skills (기술 스택)

회원가입, 프로필 수정, 프로젝트 생성 시 사용 (문자열 배열)

### Frontend
`React.js`, `Next.js`, `Vue.js`, `Angular`, `TypeScript`, `JavaScript`, `Redux`, `HTML/CSS`, `Tailwind CSS`

### Mobile
`React Native`, `Expo`, `Android`, `Swift`

### Backend
`Node.js`, `Express.js`, `Spring Boot`, `Spring`, `Java`, `Kotlin`, `Python`, `Django`, `Flask`, `Ruby on Rails`, `PHP`, `Spring WebFlux`

### ORM / Query
`JPA`, `Hibernate`, `QueryDSL`

### Database
`MySQL`, `PostgreSQL`, `MongoDB`, `Redis`, `SQLite`, `MariaDB`, `Oracle Database`, `Firebase`, `Supabase`, `Elasticsearch`

### Message Queue
`Apache Kafka`, `RabbitMQ`

### DevOps / Infra
`Docker`, `Kubernetes`, `Amazon Web Services`, `Microsoft Azure`, `Google Cloud`, `GitHub Actions`, `Jenkins`, `GitLab CI/CD`, `Cloudflare`, `Vercel`, `Terraform`, `Ansible`, `Nginx`, `Linux`

### Build Tool
`Gradle`, `Maven`

### API / Protocol
`GraphQL`, `gRPC`, `OAuth2`, `JWT`, `SSE`, `WebSocket`, `OpenAPI/Swagger`

### Collaboration
`Git`, `GitHub`, `Slack`, `Jira`, `Figma`

### Testing
`JUnit 5`, `Mockito`

### Monitoring
`Micrometer`, `Prometheus`, `Grafana`, `Logstash`, `Kibana`

### Language
`C`, `C++`

---

## 3. Gender (성별)

회원가입, 프로필 수정 시 사용

| 값 | 설명 |
|---|---|
| `MALE` | 남성 |
| `FEMALE` | 여성 |

---

## 4. ProjectCategory (프로젝트 카테고리)

프로젝트 생성/수정 시 사용

| 값 | 설명 |
|---|---|
| `ENVIRONMENT` | 친환경 |
| `PET` | 반려동물 |
| `HEALTHCARE` | 헬스케어 |
| `EDUCATION` | 교육/학습 |
| `AI_TECH` | AI/테크 |
| `FASHION_BEAUTY` | 패션/뷰티 |
| `FINANCE_PRODUCTIVITY` | 금융/생산성 |
| `ETC` | 기타 |

---

## 5. PlatformCategory (플랫폼)

프로젝트 생성/수정 시 사용

| 값 | 설명 |
|---|---|
| `IOS` | iOS |
| `ANDROID` | Android |
| `WEB` | Web |

---

## 6. ApplicationStatus (지원 결정)

지원자 수락/거절 시 사용

| 값 | 설명 |
|---|---|
| `PENDING` | 대기 중 |
| `ACCEPTED` | 수락 |
| `REJECTED` | 거절 |

---

## 7. JobField (회원 검색용 대분류)

회원 검색 필터 시 사용

| 값 | 설명 |
|---|---|
| `PLANNING` | 기획 |
| `DESIGN` | 디자인 |
| `FRONTEND` | 프론트엔드 |
| `BACKEND` | 백엔드 |
| `ETC` | 기타 |

---

## 요약 테이블

| 필드 | 타입 | 값 개수 |
|---|---|---|
| jobPositions | Enum 배열 | 11개 |
| skills | 문자열 배열 | 78개 (seed 기준) |
| gender | Enum | 2개 |
| projectCategory | Enum | 8개 |
| platformCategory | Enum | 3개 |
| applicationStatus | Enum | 3개 |
| jobField | Enum | 5개 |