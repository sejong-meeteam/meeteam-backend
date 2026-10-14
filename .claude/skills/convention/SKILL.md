---
name: convention
description: MeeTeam 프로젝트의 코드 컨벤션(CODE_CONVENTION.md)에 따라 코드를 생성하거나 검토합니다. Controller, Service, Entity, DTO 작성 시 또는 코드 리뷰가 필요할 때 사용하세요.
argument-hint: "[작업 내용] 예: 프로젝트 멤버 초대 기능 만들어줘"
allowed-tools:
  - Read
  - Edit
  - Write
  - Glob
  - Grep
---

# MeeTeam 코드 컨벤션 스킬

CODE_CONVENTION.md를 읽고 규칙에 따라 코드를 작성하거나 검토합니다.

## 사용자 요청

$ARGUMENTS

## 작업 절차

1. 먼저 `CODE_CONVENTION.md` 파일을 읽어 컨벤션 규칙을 파악하세요.
2. 사용자의 요청 유형에 따라 적절한 작업을 수행하세요.

---

## 코드 생성 시 준수 규칙

### Controller
- `@Tag`, `@Operation` Swagger 문서화 필수
- URL: `/api/v1/` 접두사 사용
- 반환 타입: `SuccessResponse<T>`
- 인증: `@AuthenticationPrincipal CustomSecurityUserDetails`

### Service
- Interface + Impl 분리
- **5단계 원칙**: 엔티티 조회 → 권한 검증 → 비즈니스 규칙 검증 → 저장 → 부가 작업
- 조회 메서드: `@Transactional(readOnly = true)`

### Entity
- `BaseEntity` 상속
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- 연관관계: `FetchType.LAZY` 필수
- 생성: `create()` 정적 팩토리 메서드
- Setter 사용 금지
- **Tell, Don't Ask 원칙**: 검증 로직은 엔티티 내부 메서드로 캡슐화
  - `is{State}()` - 상태 확인 (boolean)
  - `validate{Condition}()` - 검증 + 예외 발생

### DTO
- Java `record` 타입 사용
- `@Schema` 어노테이션 Swagger 문서화
- Response: `from(Entity)` 정적 팩토리 메서드

### 예외 처리
- `ErrorCode` enum에 코드 추가
- `CustomException` 사용

---

## 체크리스트

코드 작성 완료 후 확인:

- [ ] Controller에 `@Tag`, `@Operation` 문서화
- [ ] Request/Response DTO는 `record` 타입
- [ ] DTO에 `@Schema` 어노테이션
- [ ] Service는 Interface + Impl 분리
- [ ] Service 메서드는 5단계 원칙
- [ ] 조회 메서드 `@Transactional(readOnly = true)`
- [ ] Entity는 `BaseEntity` 상속
- [ ] Entity 연관관계 `LAZY` 로딩
- [ ] Entity 생성 `create()` 팩토리
- [ ] **Tell, Don't Ask**: 검증 로직은 엔티티 메서드로 캡슐화
- [ ] 예외는 `ErrorCode` + `CustomException`
- [ ] 응답은 `SuccessResponse.onSuccess()`