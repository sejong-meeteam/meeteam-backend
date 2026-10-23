> 기준: 공개 코드 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`의 enum·DTO·SQL seed. 테스트 및 운영 DB 적용 여부는 검증하지 않았습니다.

## 1. 기획 Field

기술 스택:  Notion, Jira, Figma, Google Analytics

### 1) PM 프로덕트 매니저

### 2) PO 프로덕트 오너

### 3) 서비스 기획

## 2. 디자인

기술 스택: Figma, Zeplin, After Effects, Premiere, Illustrator, Photoshop

### 1) UI/UX 디자이너

### 2) 모션 디자이너

### 3) BX 브랜드 디자이너

## 3. 프론트

기술 스택 : React.js, TypeScript, Next.js, Vue.js, Angular, Zustand, Redux, TailwindCSS, Swift, SwiftUI, UIKit, Firebase, Kotlin, Jetpack Compose, Coroutine, Hilt, React Native, Expo, Dart, Flutter

### 1) 웹 프론트엔드

### 2) iOS

### 3) Android

### 4) 크로스 플랫폼

## 4. 백엔드

기술 스택 :

Java
Kotlin
TypeScript
Python
Ruby on Rails
Spring Boot
Spring WebFlux
JPA
QueryDSL
MySQL
PostgreSQL
MariaDB
MongoDB
Redis
ElasticSearch
Docker
AWS
Nginx
Linux
GitHub Actions
GitLab CI/CD
Jenkins
Prometheus
Grafana
WebSocket
Kafka

RabbitMQ
NestJs

Node.js

### 1) Java/Spring

### 2) Kotlin/Spring

### 3) Node.js/NestJS

### 4) Python Backend

## 5. AI

기술 스택:

Python
NumPy
Pandas
Scikit-learn
PyTorch
TensorFlow
CUDA
Hugging Face
OpenAI API
LangChain
RAG
Vector DB
MLflow
Airflow
Docker
Kubernetes
AWS

### 1) 머신 러닝

### 2) 딥러닝

### 3) LLM

### 4) MLOps

## 6. 인프라/운영

기술 스택 : AWS, Docker, Kubernetes, Nginx, GitHub Actions, Prometheus/Grafana, Postman, Jira, Playwright, Selenium, Cypress

### 1) DevOps 엔지니어/아키텍처

### 2) QA

### 3) Cloud 엔지니어

## 데이터 기준과 사용 시 주의

- 이 목록은 `src/main/resources/db/seed/job-seed.sql`의 직군별 매핑입니다. 기술은 직군끼리 중복될 수 있으며 고유 기술 seed는 75개입니다.
- 직군·직무 코드는 `domain/job/entity/JobFieldCode.java`, `JobPositionCode.java`와 일치합니다 (Java 루트: `src/main/java/com/wardk/meeteam_backend/`).
- `GET /api/v1/jobs/options`의 현재 응답에서 직무·기술 ID를 조회합니다. DB ID를 고정값으로 가정하지 않습니다. seed 파일 존재는 운영 DB 적용 또는 시작 시 자동 실행의 증거가 아닙니다.
- 요청 형식과 회원 검색의 직군 ID 제약은 [프론트엔드 API 값 가이드](../api/FRONTEND_API_VALUES.md)를 참고합니다.
