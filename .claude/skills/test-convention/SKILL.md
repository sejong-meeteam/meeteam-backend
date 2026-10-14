---
name: test-convention
description: MeeTeam 프로젝트의 테스트 컨벤션(TEST_CONVENTION.md)에 따라 단위 테스트, 통합 테스트, 인수 테스트를 생성합니다. 테스트 코드 작성 시 사용하세요.
argument-hint: "[테스트 대상] 예: Project 엔티티 테스트 작성해줘, ProjectQueryService 통합 테스트 작성해줘"
allowed-tools:
  - Read
  - Edit
  - Write
  - Glob
  - Grep
  - Bash
---

# MeeTeam 테스트 컨벤션 스킬

TEST_CONVENTION.md를 읽고 규칙에 따라 테스트 코드를 작성합니다.

## 사용자 요청

$ARGUMENTS

## 작업 절차

1. 먼저 `TEST_CONVENTION.md` 파일을 읽어 테스트 규칙을 파악하세요.
2. 테스트 대상 클래스의 코드를 읽고 분석하세요.
3. 사용자 요청에 맞는 테스트 유형을 선택하세요.
4. 컨벤션에 맞게 테스트 코드를 작성하세요.

---

## 테스트 전략

| 테스트 유형 | 범위 | 목적 |
|-----------|------|------|
| 단위 테스트 | Entity | 엔티티 행동, 검증 로직 |
| 통합 테스트 | Service → Repository | 실제 DB 연동, DTO 검증 |
| 인수 테스트 | Controller → DB (E2E) | 사용자 시나리오 |

---

## 단위 테스트 (Entity)

엔티티의 행동(비즈니스 로직, 검증 메서드)을 테스트합니다.

**위치**: `src/test/java/.../unit/{도메인}/entity/`

```java
@DisplayName("Project 엔티티 테스트")
class ProjectTest {

    @Nested
    @DisplayName("isLeader 메서드는")
    class IsLeader {

        @Test
        @DisplayName("생성자의 ID와 일치하면 true를 반환한다")
        void returns_true_when_creator_id_matches() {
            // given
            Member creator = MemberFixture.withId(1L);
            Project project = ProjectFixture.withCreator(creator);

            // when
            boolean result = project.isLeader(1L);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("validateLeaderPermission 메서드는")
    class ValidateLeaderPermission {

        @Test
        @DisplayName("리더가 아니면 CustomException을 던진다")
        void throws_exception_when_not_leader() {
            // given
            Member creator = MemberFixture.withId(1L);
            Project project = ProjectFixture.withCreator(creator);

            // when & then
            assertThatThrownBy(() -> project.validateLeaderPermission(999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_MEMBER_FORBIDDEN);
        }
    }
}
```

**테스트 대상 메서드**:
- `is{State}()` - 상태 확인
- `validate{Condition}()` - 검증 + 예외
- `create()` - 팩토리 메서드
- `update{Field}()` - 상태 변경

---

## 통합 테스트 (Service)

Service 메서드를 실제 DB와 연동하여 테스트하고, **반환되는 DTO를 검증**합니다.

**위치**: `src/test/java/.../integration/{도메인}/service/`

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ProjectQueryService 통합 테스트")
class ProjectQueryServiceIntegrationTest {

    @Autowired
    private ProjectQueryService projectQueryService;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("키워드가 프로젝트명에 포함된 프로젝트의 DTO를 반환한다")
    void returns_dto_containing_keyword_in_name() {
        // given
        Project saved = projectRepository.save(ProjectFixture.withName("AI 개발", leader));

        // when
        Page<ProjectCardResponse> result = projectQueryService.searchV1(request, pageable, null);

        // then - DTO 필드 검증
        ProjectCardResponse dto = result.getContent().get(0);
        assertThat(dto.projectId()).isEqualTo(saved.getId());
        assertThat(dto.projectName()).isEqualTo("AI 개발");
        assertThat(dto.creatorName()).isEqualTo(leader.getRealName());
    }
}
```

**DTO 검증 포인트**:
- 필드 값 검증
- 연관 데이터 변환 검증
- 계산된 필드 검증 (`isLiked` 등)
- 목록 정렬 순서 검증
- 페이징 검증

---

## 체크리스트

**단위 테스트 (Entity)**
- [ ] 스프링 컨텍스트 없이 순수 Java
- [ ] `@Nested`로 메서드별 그룹화
- [ ] `@DisplayName` 한국어 설명
- [ ] 엔티티 행동 메서드 검증 (`is*`, `validate*`, `create`)

**통합 테스트 (Service)**
- [ ] `@SpringBootTest` + `@ActiveProfiles("test")`
- [ ] `@Transactional`로 테스트 격리
- [ ] Fixture 클래스로 테스트 데이터 생성
- [ ] **반환 DTO 필드 검증**