# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MeeTeam Backend - A Spring Boot API server for a developer team matching and project collaboration platform. Features include team matching, project management, real-time WebSocket chat, and SSE notifications.

## Build & Development Commands

```bash
# Build the project (also runs QueryDSL code generation)
./gradlew clean build

# Run tests
./gradlew test

# Run application locally
./gradlew bootRun

# Generate QueryDSL Q-classes only
./gradlew compileQuerydsl

# Start infrastructure (Redis, RabbitMQ, Prometheus, Grafana)
docker-compose up -d
```

**Local Development Requirements:**
- Java 17
- MySQL 8.0+
- Redis 7.0+
- RabbitMQ with STOMP plugin (for WebSocket)

## Architecture

**Layered Architecture with DDD principles:**
```
web/        → Controllers (presentation layer)
domain/     → Business logic, entities, repositories per domain
global/     → Cross-cutting concerns (config, auth, exceptions, response)
```

**Domain modules:** applicant, category, chat, file, member, notification, project, projectLike, projectMember, review, skill

**Key patterns:**
- All entities extend `BaseEntity` (provides `createdAt`, `editedAt` with JPA auditing)
- Standardized responses via `SuccessResponse<T>` with `SuccessCode` enum
- Custom exceptions via `CustomException` with `ErrorCode` enum - add new error codes to `global/response/ErrorCode.java`
- Event-driven notifications using Spring events + `@TransactionalEventListener` + `@Async`
- Optimistic locking with `@Retry` AOP aspect for concurrent updates
- QueryDSL for complex queries (Q-classes generated in `build/generated/querydsl`)

## API Response Format

```java
// Success response
SuccessResponse.onSuccess(data)           // Uses default _OK code
SuccessResponse.of(SuccessCode.XXX, data) // Uses specific code

// Error handling - throw CustomException
throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
```

## Key Configuration

- **Authentication:** 세종대 포털 인증 + JWT (Access: 10h, Refresh: 7d)
  - OAuth2/자체 로그인은 제거됨 (2026-04-27)
  - 세종대 학번/비밀번호로 로그인 → JWT 발급
  - Token Rotation 적용 (Refresh 시 새 RT 발급)
- **WebSocket:** STOMP over RabbitMQ (port 61613), endpoint `/ws`
- **Caching:** EhCache for main page projects, Redis for sessions
- **File storage:** AWS S3

## Chat Room Types

`PROJECT` (team chat), `TOPIC` (subject-based), `PRIVATE` (1:1)

## Testing

Uses JUnit 5 with H2 in-memory database. Tests located in `src/test/java/`.

## Code Documentation
1
Javadoc required for public classes/methods. Use `@Operation` annotation on controller methods for Swagger documentation. API docs available at `/swagger-ui.html`.

## Code Convention

새로운 기능 개발 or 리펙토링 시 [CODE_CONVENTION.md](./docs/CODE_CONVENTION.md)를 참고하세요. Controller, Service, Entity, DTO 작성 규칙과 전체 예시 코드가 포함되어 있습니다.

## 세종대 포털 SSL 호환성

세종대 포털 서버가 SHA1 기반 인증서/서명 알고리즘을 사용하여 Java 17 기본 보안 정책과 충돌합니다.

**해결 방법:**
- `custom.java.security` 파일에서 SHA1 알고리즘 제한 해제
- Dockerfile에서 `-Djava.security.properties` 옵션으로 커스텀 보안 설정 적용

**관련 파일:**
- `custom.java.security` - SHA1 제한 제거된 보안 설정
- `Dockerfile` - 커스텀 보안 설정 적용
- `SejongPortalClient.java` - 세종대 포털 인증 클라이언트

**주의:** 세종대 포털 관련 SSL 오류 발생 시 `jdk.tls.disabledAlgorithms`, `jdk.certpath.disabledAlgorithms` 설정 확인 필요.
