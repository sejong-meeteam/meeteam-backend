# MeeTeam Backend 코드 컨벤션

이 문서는 MeeTeam Backend 프로젝트의 코드 작성 규칙을 정의합니다.
모든 팀원은 새로운 기능 개발 시 이 컨벤션을 준수해야 합니다.

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [패키지 구조](#2-패키지-구조)
3. [Controller 작성 규칙](#3-controller-작성-규칙)
4. [Service 작성 규칙](#4-service-작성-규칙)
5. [Entity 작성 규칙](#5-entity-작성-규칙)
6. [DTO 작성 규칙](#6-dto-작성-규칙)
7. [예외 처리 규칙](#7-예외-처리-규칙)
8. [API 응답 형식](#8-api-응답-형식)
9. [이벤트 기반 알림](#9-이벤트-기반-알림)
10. [전체 예시 코드](#10-전체-예시-코드)

---

## 1. 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                         web/ (Presentation)                      │
│   Controller → Request DTO 수신 → Service 호출 → Response 반환   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      domain/ (Business Logic)                    │
│   Service → Entity 조회/검증 → 비즈니스 로직 → Repository 저장    │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Repository (Data)                        │
│            JPA Repository + QueryDSL Custom Repository           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 패키지 구조

```
src/main/java/com/wardk/meeteam_backend/
├── web/                           # Presentation Layer
│   └── {도메인}/
│       ├── controller/            # REST Controller
│       └── dto/
│           ├── request/           # 요청 DTO
│           └── response/          # 응답 DTO
│
├── domain/                        # Business Layer
│   └── {도메인}/
│       ├── entity/                # JPA Entity
│       ├── repository/            # Repository Interface
│       └── service/               # Service Interface + Impl
│
└── global/                        # Cross-cutting Concerns
    ├── config/                    # 설정 클래스
    ├── exception/                 # 예외 처리
    ├── response/                  # 응답 형식 (SuccessResponse, ErrorCode)
    └── entity/                    # 공통 Entity (BaseEntity)
```

---

## 3. Controller 작성 규칙

### 3.1 기본 원칙

- **단일 책임**: Controller는 요청 수신과 응답 반환만 담당
- **비즈니스 로직 금지**: 모든 비즈니스 로직은 Service에 위임
- **Swagger 문서화 필수**: `@Tag`, `@Operation` 어노테이션 사용

### 3.2 클래스 구조

```java
@Tag(name = "도메인명", description = "도메인 설명 API")
@RestController
@RequiredArgsConstructor
public class {도메인}Controller {

    private final {도메인}Service service;

    @Operation(summary = "API 요약", description = "API 상세 설명")
    @PostMapping("/api/v1/{경로}")
    public SuccessResponse<{Response}> methodName(
            @PathVariable Long id,
            @RequestBody @Validated {Request} request,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        {Response} response = service.method(id, userDetails.getMemberId(), request);

        return SuccessResponse.onSuccess(response);
    }
}
```

### 3.3 규칙

| 항목 | 규칙 |
|------|------|
| URL 버전 | `/api/v1/` 접두사 사용 |
| HTTP Method | POST(생성), GET(조회), PUT(수정), DELETE(삭제) |
| 요청 검증 | `@Validated` 또는 `@Valid` 사용 |
| 인증 정보 | `@AuthenticationPrincipal CustomSecurityUserDetails` 사용 |
| 반환 타입 | `SuccessResponse<T>` 사용 |

---

## 4. Service 작성 규칙

### 4.1 인터페이스 + 구현체 분리

```java
// Interface
public interface {도메인}Service {
    {Response} method(Long id, Long memberId, {Request} request);
}

// Implementation
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class {도메인}ServiceImpl implements {도메인}Service {
    // 구현
}
```

### 4.2 메서드 구조 (5단계 원칙)

```java
@Override
public {Response} method(Long id, Long memberId, {Request} request) {
    // 1단계: 엔티티 조회 및 기본 검증
    Entity entity = repository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

    // 2단계: 권한 검증
    validatePermission(entity, memberId);

    // 3단계: 비즈니스 규칙 검증
    validateBusinessRules(entity, request);

    // 4단계: 엔티티 생성/수정 및 저장
    Entity newEntity = Entity.create(...);
    Entity saved = repository.save(newEntity);

    // 5단계: 부가 작업 (알림, 이벤트 등)
    eventPublisher.publishEvent(new NotificationEvent(...));

    return {Response}.from(saved);
}
```

### 4.3 트랜잭션 규칙

| 어노테이션 | 사용 시점 |
|-----------|----------|
| `@Transactional` | 클래스 레벨 (기본값) |
| `@Transactional(readOnly = true)` | 조회 전용 메서드 |
| `@Transactional(propagation = Propagation.NEVER)` | 트랜잭션 분리 필요 시 |

### 4.4 검증 메서드 분리

```java
// 검증 로직은 private 메서드로 분리
private void validatePermission(Project project, String requesterEmail) {
    if (!project.getCreator().getEmail().equals(requesterEmail)) {
        throw new CustomException(ErrorCode.PROJECT_EDIT_FORBIDDEN);
    }
}

private static void validateProjectRecruiting(Project project) {
    if (project.isCompleted()) {
        throw new CustomException(ErrorCode.PROJECT_RECRUITMENT_SUSPENDED);
    }
}
```

---

## 5. Entity 작성 규칙

### 5.1 기본 구조

```java
/**
 * 엔티티 설명.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "테이블명")
public class EntityName extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "엔티티_id")
    private Long id;

    // 연관관계는 LAZY 로딩 필수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "외래키_id", nullable = false)
    private OtherEntity otherEntity;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private StatusEnum status;

    // 생성자는 private + Builder
    @Builder
    private EntityName(/* 파라미터 */) {
        // 초기화
    }

    // 정적 팩토리 메서드
    public static EntityName create(/* 파라미터 */) {
        return EntityName.builder()
                .field(value)
                .build();
    }

    // 상태 변경 메서드
    public void updateStatus(StatusEnum status) {
        this.status = status;
    }
}
```

### 5.2 규칙

| 항목 | 규칙 |
|------|------|
| 기본 생성자 | `@NoArgsConstructor(access = AccessLevel.PROTECTED)` |
| Setter | **사용 금지** - 명시적 메서드로 상태 변경 |
| 연관관계 | `FetchType.LAZY` 필수 |
| Enum 저장 | `@Enumerated(EnumType.STRING)` |
| 상속 | `BaseEntity` 상속 (createdAt, editedAt 자동 관리) |
| 객체 생성 | `create()` 정적 팩토리 메서드 사용 |
| 검증 로직 | **Tell, Don't Ask 원칙** - 엔티티 내부 메서드로 캡슐화 |

### 5.3 Tell, Don't Ask 원칙

객체의 상태를 getter로 꺼내서 외부에서 검증하지 말고, 객체에게 직접 물어보거나 행동을 요청합니다.

```java
// ❌ Bad - getter로 꺼내서 외부에서 검증
if (!project.getCreator().getEmail().equals(requesterEmail)) {
    throw new CustomException(ErrorCode.PROJECT_MEMBER_FORBIDDEN);
}

// ✅ Good - 엔티티에게 검증 요청
project.validateLeaderPermission(requesterEmail);
```

**Entity에 검증 메서드 작성 예시:**

```java
@Entity
public class Project extends BaseEntity {

    // 조회용 메서드 (boolean 반환)
    public boolean isLeader(String email) {
        return this.creator.getEmail().equals(email);
    }

    public boolean isLeader(Long memberId) {
        return this.creator.getId().equals(memberId);
    }

    // 검증용 메서드 (예외 발생)
    public void validateLeaderPermission(String requesterEmail) {
        if (!isLeader(requesterEmail)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_FORBIDDEN);
        }
    }

    public void validateNotCompleted() {
        if (isCompleted()) {
            throw new CustomException(ErrorCode.PROJECT_ALREADY_COMPLETED);
        }
    }

    public void validateEditable() {
        if (!isEditable()) {
            throw new CustomException(ErrorCode.PROJECT_EDIT_NOT_ALLOWED_SUSPENDED);
        }
    }
}
```

**Service에서 사용:**

```java
@Override
public MemberExpelResponse expelMember(Long projectId, Long memberId, String requesterEmail) {
    Project project = projectRepository.findActiveById(projectId)
            .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

    // Tell, Don't Ask 적용
    project.validateNotCompleted();
    project.validateLeaderPermission(requesterEmail);

    if (project.isLeader(memberId)) {
        throw new CustomException(ErrorCode.CREATOR_DELETE_FORBIDDEN);
    }

    // ... 비즈니스 로직
}
```

**네이밍 규칙:**

| 패턴 | 반환 타입 | 설명 |
|------|----------|------|
| `is{State}()` | boolean | 상태 확인 (isLeader, isCompleted, isEditable) |
| `has{Something}()` | boolean | 소유 확인 (hasMember, hasPermission) |
| `can{Action}()` | boolean | 행동 가능 여부 (canEdit, canDelete) |
| `validate{Condition}()` | void | 조건 검증 + 예외 발생 (validateLeaderPermission) |

---

## 6. DTO 작성 규칙

### 6.1 Request DTO (Java Record 사용)

```java
/**
 * 요청 DTO 설명.
 */
@Schema(description = "요청 설명")
public record {Action}Request(
        @NotNull
        @Schema(description = "필드 설명", example = "예시값")
        FieldType fieldName,

        @NotBlank
        @Schema(description = "필드 설명", example = "예시 텍스트")
        String textField
) {
}
```

### 6.2 Response DTO (Java Record + 정적 팩토리)

```java
/**
 * 응답 DTO 설명.
 */
@Schema(description = "응답 설명")
public record {Action}Response(
        @Schema(description = "ID")
        Long id,

        @Schema(description = "상태")
        StatusEnum status
) {
    /**
     * Entity를 Response DTO로 변환합니다.
     */
    public static {Action}Response from(Entity entity) {
        return new {Action}Response(
                entity.getId(),
                entity.getStatus()
        );
    }
}
```

### 6.3 규칙

| 항목 | 규칙 |
|------|------|
| 타입 | Java 17+ `record` 타입 사용 (불변성 보장) |
| 검증 | Request에 `@NotNull`, `@NotBlank` 등 Bean Validation 사용 |
| 문서화 | `@Schema` 어노테이션으로 Swagger 문서화 |
| 변환 | Response에 `from(Entity)` 정적 팩토리 메서드 제공 |
| Javadoc | 모든 public DTO에 Javadoc 작성 |

---

## 7. 예외 처리 규칙

### 7.1 ErrorCode 정의

```java
// global/response/ErrorCode.java에 추가
@Getter
public enum ErrorCode {
    // 도메인별로 그룹핑
    // {도메인}
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "{DOMAIN}404", "메시지"),
    ENTITY_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "{DOMAIN}400", "메시지"),
    ENTITY_FORBIDDEN(HttpStatus.FORBIDDEN, "{DOMAIN}403", "메시지");

    // 생성자, 필드 생략
}
```

### 7.2 예외 발생

```java
// 엔티티 조회 실패
Entity entity = repository.findById(id)
        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

// 권한 검증 실패
if (!hasPermission) {
    throw new CustomException(ErrorCode.ENTITY_FORBIDDEN);
}

// 비즈니스 규칙 위반
if (isDuplicate) {
    throw new CustomException(ErrorCode.ENTITY_ALREADY_EXISTS);
}
```

### 7.3 ErrorCode 네이밍 규칙

| 패턴 | 설명 | 예시 |
|------|------|------|
| `{ENTITY}_NOT_FOUND` | 리소스 없음 | `PROJECT_NOT_FOUND` |
| `{ENTITY}_ALREADY_EXISTS` | 중복 존재 | `PROJECT_APPLICATION_ALREADY_EXISTS` |
| `{ENTITY}_FORBIDDEN` | 권한 없음 | `PROJECT_EDIT_FORBIDDEN` |
| `{ACTION}_{REASON}` | 동작 불가 | `APPLICATION_SELF_PROJECT_FORBIDDEN` |

---

## 8. API 응답 형식

### 8.1 성공 응답

```java
// 기본 성공 (code: "OK", message: "요청이 성공했습니다")
return SuccessResponse.onSuccess(response);

// 커스텀 성공 코드
return SuccessResponse.of(SuccessCode.CREATED, response);
```

### 8.2 응답 JSON 형식

```json
{
  "code": "OK",
  "message": "요청이 성공했습니다",
  "result": {
    "applicationId": 1,
    "projectId": 10,
    "status": "PENDING"
  }
}
```

---

## 9. 이벤트 기반 알림

### 9.1 이벤트 발행 (Service 내)

```java
// 1. 알림 데이터 저장
notificationSaveService.saveForApply(receiver, actor, project, referenceId);

// 2. SSE 이벤트 발행
eventPublisher.publishEvent(new NotificationEvent(
        receiverId,      // 수신자 ID
        projectId,       // 프로젝트 ID
        actorId,         // 행위자 ID
        NotificationType.PROJECT_APPLY,  // 알림 타입
        referenceId      // 참조 ID (optional)
));
```

### 9.2 규칙

- 알림 저장과 SSE 이벤트 발행은 **분리**
- 이벤트는 `@TransactionalEventListener`로 **비동기** 처리
- 트랜잭션 커밋 후 이벤트 발행 (`AFTER_COMMIT`)

---

## 10. 전체 예시 코드

> 아래는 **프로젝트 지원** 기능의 전체 구현 예시입니다.

### 10.1 Controller

```java
package com.wardk.meeteam_backend.web.application.controller;

import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.application.dto.request.ApplicationRequest;
import com.wardk.meeteam_backend.web.application.dto.response.ApplicationResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "프로젝트 지원", description = "프로젝트 지원 관련 API")
@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ProjectApplicationService applicationService;

    @Operation(summary = "프로젝트 지원", description = "프로젝트에 지원합니다.")
    @PostMapping("/api/v1/projects/{projectId}/application")
    public SuccessResponse<ApplicationResponse> apply(
            @PathVariable Long projectId,
            @RequestBody @Validated ApplicationRequest request,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        ApplicationResponse response = applicationService.apply(
                projectId, userDetails.getMemberId(), request);

        return SuccessResponse.onSuccess(response);
    }
}
```

### 10.2 Request DTO

```java
package com.wardk.meeteam_backend.web.application.dto.request;

import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 프로젝트 지원 요청 DTO.
 */
@Schema(description = "프로젝트 지원 요청")
public record ApplicationRequest(
        @NotNull
        @Schema(description = "지원할 포지션 코드", example = "WEB_FRONTEND")
        JobPositionCode jobPositionCode,

        @NotBlank
        @Schema(description = "지원 사유 및 자기소개", example = "이 프로젝트에 참여하고 싶습니다.")
        String motivation
) {
}
```

### 10.3 Response DTO

```java
package com.wardk.meeteam_backend.web.application.dto.response;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로젝트 지원 응답 DTO.
 */
@Schema(description = "프로젝트 지원 응답")
public record ApplicationResponse(
        @Schema(description = "지원서 ID")
        Long applicationId,

        @Schema(description = "프로젝트 ID")
        Long projectId,

        @Schema(description = "지원자 ID")
        Long applicantId,

        @Schema(description = "지원 상태")
        ApplicationStatus status
) {
    public static ApplicationResponse from(ProjectApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getProject().getId(),
                application.getApplicant().getId(),
                application.getStatus()
        );
    }
}
```

### 10.4 Service Interface

```java
package com.wardk.meeteam_backend.domain.application.service;

import com.wardk.meeteam_backend.web.application.dto.request.ApplicationRequest;
import com.wardk.meeteam_backend.web.application.dto.response.ApplicationResponse;

public interface ProjectApplicationService {
    ApplicationResponse apply(Long projectId, Long memberId, ApplicationRequest request);
}
```

### 10.5 Service Implementation

```java
package com.wardk.meeteam_backend.domain.application.service;

import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.repository.RecruitmentStateRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.application.dto.request.ApplicationRequest;
import com.wardk.meeteam_backend.web.application.dto.response.ApplicationResponse;
import io.micrometer.core.annotation.Counted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 지원 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectApplicationServiceImpl implements ProjectApplicationService {

    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final ProjectApplicationRepository applicationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final RecruitmentStateRepository recruitmentStateRepository;
    private final NotificationSaveService notificationSaveService;
    private final ApplicationEventPublisher eventPublisher;

    @Counted("project.apply")
    @Override
    public ApplicationResponse apply(Long projectId, Long memberId, ApplicationRequest request) {
        // 1단계: 엔티티 조회 및 기본 검증
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        validateProjectRecruiting(project);

        // 2단계: 권한 검증 (자기 프로젝트 지원 불가)
        if (project.getCreator().getId().equals(memberId)) {
            throw new CustomException(ErrorCode.APPLICATION_SELF_PROJECT_FORBIDDEN);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 3단계: 비즈니스 규칙 검증
        if (applicationRepository.existsByProjectAndApplicant(project, member)) {
            throw new CustomException(ErrorCode.PROJECT_APPLICATION_ALREADY_EXISTS);
        }

        JobPosition jobPosition = jobPositionRepository.findByCode(request.jobPositionCode())
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSITION_NOT_FOUND));

        RecruitmentState recruitmentState = recruitmentStateRepository
                .findByProjectIdAndJobPosition(projectId, jobPosition)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_POSITION_NOT_RECRUITING));

        if (recruitmentState.isClosed()) {
            throw new CustomException(ErrorCode.RECRUITMENT_POSITION_CLOSED);
        }

        // 4단계: 엔티티 생성 및 저장
        ProjectApplication application = ProjectApplication.create(
                project, member, jobPosition, request.motivation()
        );
        ProjectApplication savedApplication = applicationRepository.save(application);

        // 5단계: 알림 처리
        Long receiverId = project.getCreator().getId();
        Long actorId = member.getId();

        notificationSaveService.saveForApply(
                project.getCreator(), member, project, savedApplication.getId()
        );

        eventPublisher.publishEvent(new NotificationEvent(
                receiverId, project.getId(), actorId,
                NotificationType.PROJECT_APPLY, savedApplication.getId()
        ));

        log.info("프로젝트 지원 완료 - projectId: {}, applicantId: {}", projectId, memberId);

        return ApplicationResponse.from(savedApplication);
    }

    private static void validateProjectRecruiting(Project project) {
        if (project.isCompleted()) {
            throw new CustomException(ErrorCode.PROJECT_RECRUITMENT_SUSPENDED);
        }
    }
}
```

### 10.6 Entity

```java
package com.wardk.meeteam_backend.domain.application.entity;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 지원서 엔티티.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_member_application")
public class ProjectApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @Column(length = 800)
    private String motivation;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Builder
    private ProjectApplication(Project project, Member applicant,
                               JobPosition jobPosition, String motivation) {
        this.project = project;
        this.applicant = applicant;
        this.jobPosition = jobPosition;
        this.motivation = motivation;
        this.status = ApplicationStatus.PENDING;
    }

    public static ProjectApplication create(Project project, Member applicant,
                                            JobPosition jobPosition, String motivation) {
        return ProjectApplication.builder()
                .project(project)
                .applicant(applicant)
                .jobPosition(jobPosition)
                .motivation(motivation)
                .build();
    }

    public void updateStatus(ApplicationStatus status) {
        this.status = status;
    }
}
```

---

## Checklist

새 기능 개발 시 아래 체크리스트를 확인하세요:

- [ ] Controller에 `@Tag`, `@Operation` 문서화 적용
- [ ] Request/Response DTO는 `record` 타입 사용
- [ ] DTO에 `@Schema` 어노테이션으로 Swagger 문서화
- [ ] Service는 Interface + Impl 분리
- [ ] Service 메서드는 5단계 원칙 적용
- [ ] 조회 메서드에 `@Transactional(readOnly = true)` 적용
- [ ] Entity는 `BaseEntity` 상속
- [ ] Entity 연관관계는 `LAZY` 로딩
- [ ] Entity 생성은 `create()` 정적 팩토리 사용
- [ ] 예외 발생 시 `ErrorCode` enum에 코드 추가 후 `CustomException` 사용
- [ ] API 응답은 `SuccessResponse.onSuccess()` 사용
- [ ] 알림 필요 시 이벤트 기반으로 처리

---

*Last Updated: 2026-03-17*