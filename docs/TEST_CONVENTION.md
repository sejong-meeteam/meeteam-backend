# MeeTeam Backend 테스트 컨벤션

---

## 1. 테스트 전략

| 테스트 유형 | 범위 | 목적 |
|-----------|------|------|
| 단위 테스트 | Entity | 엔티티 행동, 검증 로직 |
| 통합 테스트 | Service → Repository | 실제 DB 연동, DTO 검증 |
| 인수 테스트 | Controller → DB (E2E) | 사용자 시나리오 |

---

## 2. 패키지 구조

```
src/test/java/com/wardk/meeteam_backend/
├── unit/                          # 단위 테스트
│   └── {도메인}/
│       └── entity/                # 엔티티 행동 테스트
│
├── integration/                   # 통합 테스트
│   └── {도메인}/
│       └── service/               # Service + Repository
│
├── acceptance/cucumber/           # 인수 테스트 (기존 구조)
│
└── fixture/                       # 공통 테스트 픽스처

src/test/resources/
└── sql/                           # 시드 데이터 SQL 파일
    └── {도메인}-{기능}-data.sql
```

---

## 3. 단위 테스트 (Entity)

엔티티의 행동(비즈니스 로직, 검증 메서드)을 테스트합니다.

### 3.1 기본 구조

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

        @Test
        @DisplayName("생성자의 ID와 일치하지 않으면 false를 반환한다")
        void returns_false_when_creator_id_not_matches() {
            // given
            Member creator = MemberFixture.withId(1L);
            Project project = ProjectFixture.withCreator(creator);

            // when
            boolean result = project.isLeader(999L);

            // then
            assertThat(result).isFalse();
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

### 3.2 테스트 대상

| 메서드 패턴 | 설명 | 예시 |
|-----------|------|------|
| `is{State}()` | 상태 확인 | `isLeader()`, `isCompleted()`, `isEditable()` |
| `has{Something}()` | 소유 확인 | `hasMember()`, `hasPermission()` |
| `can{Action}()` | 행동 가능 여부 | `canEdit()`, `canDelete()` |
| `validate{Condition}()` | 검증 + 예외 | `validateLeaderPermission()`, `validateNotCompleted()` |
| `create()` | 팩토리 메서드 | 초기 상태 검증 |
| `update{Field}()` | 상태 변경 | 변경 후 상태 검증 |

---

## 4. 통합 테스트 (Service)

Service 메서드를 실제 DB와 연동하여 테스트하고, **반환되는 DTO를 검증**합니다.

### 4.1 기본 구조 (시드 데이터 방식)

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/project-search-data.sql")
@DisplayName("ProjectQueryService 통합 테스트")
class ProjectQueryServiceIntegrationTest {

    // ==================== 상수 (시드 데이터 기반) ====================

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int TOTAL_SEED_PROJECTS = 20;

    // 시드 데이터의 카테고리별 개수
    private static final int AI_TECH_COUNT = 8;
    private static final int HEALTHCARE_COUNT = 2;

    // 시드 데이터의 모집상태별 개수
    private static final int RECRUITING_COUNT = 16;
    private static final int CLOSED_COUNT = 2;

    // 키워드 검색 상수
    private static final String KEYWORD_AI = "AI";
    private static final String KEYWORD_NOT_EXIST = "존재하지않는키워드XYZ";

    // ==================== 의존성 ====================

    @Autowired
    private ProjectQueryService projectQueryService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MemberRepository memberRepository;

    // ==================== 테스트 ====================

    @Nested
    @DisplayName("searchV1 메서드는")
    class SearchV1 {

        @Nested
        @DisplayName("키워드 검색 시")
        class WithKeyword {

            @Test
            @DisplayName("프로젝트명에 키워드가 포함된 프로젝트를 반환한다")
            void returns_projects_when_keyword_matches_project_name() {
                // given - 시드 데이터 사용

                // when
                Page<ProjectCardResponse> result = search(requestWithKeyword(KEYWORD_AI));

                // then
                assertThat(result.getContent()).isNotEmpty();
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getProjectName().contains(KEYWORD_AI));
            }
        }

        @Nested
        @DisplayName("카테고리 필터 시")
        class WithCategoryFilter {

            @Test
            @DisplayName("AI_TECH 카테고리의 프로젝트만 반환한다")
            void returns_only_ai_tech_projects() {
                // when
                Page<ProjectCardResponse> result = search(requestWithCategory(ProjectCategory.AI_TECH));

                // then
                assertThat(result.getContent()).hasSize(AI_TECH_COUNT);
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getCategoryCode().equals(ProjectCategory.AI_TECH.name()));
            }
        }
    }

    // ==================== 헬퍼 메서드 ====================

    private Pageable defaultPageable() {
        return PageRequest.of(DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }

    private ProjectSearchRequest requestWithKeyword(String keyword) {
        return new ProjectSearchRequest(keyword, null, null, null, null, null, null);
    }

    private ProjectSearchRequest requestWithCategory(ProjectCategory category) {
        return new ProjectSearchRequest(null, category, null, null, null, null, null);
    }

    private ProjectSearchRequest emptyRequest() {
        return new ProjectSearchRequest(null, null, null, null, null, null, null);
    }

    private Page<ProjectCardResponse> search(ProjectSearchRequest request) {
        return projectQueryService.searchV1(request, defaultPageable(), null);
    }

    private Page<ProjectCardResponse> search(ProjectSearchRequest request, Pageable pageable) {
        return projectQueryService.searchV1(request, pageable, null);
    }
}
```

### 4.2 시드 데이터 SQL 파일

```sql
-- src/test/resources/sql/project-search-data.sql

-- @Nested + @Sql 조합에서 트랜잭션 경계 이슈로 DELETE 필요
DELETE FROM project;
DELETE FROM member;

-- 회원 데이터
INSERT INTO member (member_id, email, real_name, role, version, created_at) VALUES
(1, 'hong@example.com', '홍길동', 'USER', 0, '2024-01-01 09:00:00'),
(2, 'kim@example.com', '김철수', 'USER', 0, '2024-01-01 09:00:00');

-- 프로젝트 데이터 (다양한 조건)
INSERT INTO project (project_id, creator_id, project_name, ..., created_at) VALUES
-- AI_TECH 카테고리
(1, 1, 'AI 챗봇 개발', ..., '2024-01-01 10:00:00'),
(2, 2, 'AI 이미지 분석', ..., '2024-01-02 10:00:00'),
-- HEALTHCARE 카테고리
(3, 1, '헬스케어 앱', ..., '2024-01-03 10:00:00');
```

### 4.3 테스트 파일 구조

```java
class IntegrationTest {
    // 1. 상수 (시드 데이터 기반)
    private static final int AI_TECH_COUNT = 8;

    // 2. 의존성
    @Autowired
    private SomeService service;

    // 3. 테스트 (@Nested 구조)
    @Nested
    class SomeMethod {
        @Test
        void test_case() { }
    }

    // 4. 헬퍼 메서드 (맨 아래)
    private Request createRequest() { }
    private Response search() { }
}
```

### 4.4 @Sql 사용 시 주의사항

| 항목 | 설명 |
|------|------|
| **DELETE 필수** | `@Nested` + `@Sql` 조합에서 트랜잭션 롤백 이슈로 DELETE 필요 |
| **created_at 포함** | 정렬 테스트 시 `created_at` 값 명시 필요 |
| **FK 순서** | DELETE: 자식 → 부모, INSERT: 부모 → 자식 |
| **ID 명시** | PK 값을 명시하여 테스트 예측 가능성 확보 |

### 4.5 헬퍼 메서드 패턴

```java
// Request 생성 헬퍼
private ProjectSearchRequest requestWithKeyword(String keyword) {
    return new ProjectSearchRequest(keyword, null, null, null, null, null, null);
}

private ProjectSearchRequest requestWithCategory(ProjectCategory category) {
    return new ProjectSearchRequest(null, category, null, null, null, null, null);
}

private ProjectSearchRequest emptyRequest() {
    return new ProjectSearchRequest(null, null, null, null, null, null, null);
}

// 검색 실행 헬퍼
private Page<ProjectCardResponse> search(ProjectSearchRequest request) {
    return projectQueryService.searchV1(request, defaultPageable(), null);
}

// Pageable 헬퍼
private Pageable defaultPageable() {
    return PageRequest.of(DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
}

private Pageable pageableOf(int page, int size) {
    return PageRequest.of(page, size);
}

// 결과 추출 헬퍼
private ProjectCardResponse getFirstResult(Page<ProjectCardResponse> result) {
    return result.getContent().get(0);
}
```

### 4.6 상수 추출 패턴

```java
// 페이징 상수
private static final int DEFAULT_PAGE = 0;
private static final int DEFAULT_PAGE_SIZE = 20;
private static final int TOTAL_SEED_PROJECTS = 20;

// 시드 데이터 개수 (카테고리별, 상태별 등)
private static final int AI_TECH_COUNT = 8;
private static final int RECRUITING_COUNT = 16;

// 검색 키워드
private static final String KEYWORD_AI = "AI";
private static final String KEYWORD_NOT_EXIST = "존재하지않는키워드XYZ";
```

### 4.7 DTO 검증 포인트

```java
// 필드 값 검증
assertThat(dto.getProjectId()).isEqualTo(expected.getId());
assertThat(dto.getProjectName()).isEqualTo("AI 개발");

// 연관 데이터 변환 검증
assertThat(dto.getCreatorName()).isEqualTo(leader.getRealName());
assertThat(dto.getCategoryName()).isEqualTo(category.getDisplayName());

// 계산된 필드 검증
assertThat(dto.isLiked()).isFalse();

// 목록 순서 검증
assertThat(result.getContent())
    .extracting(ProjectCardResponse::getProjectName)
    .containsExactly("첫번째", "두번째", "세번째");

// 전체 매칭 검증
assertThat(result.getContent())
    .allMatch(dto -> dto.getCategoryCode().equals(ProjectCategory.AI_TECH.name()));

// 전체 필드 검증 (allSatisfy)
assertThat(result.getContent()).allSatisfy(dto -> {
    assertThat(dto.getProjectId()).isNotNull();
    assertThat(dto.getProjectName()).isNotNull();
    assertThat(dto.getCreatorName()).isNotNull();
});
```

---

## 5. 인수 테스트 (Cucumber)

### 5.1 Feature 파일

```gherkin
# language: ko

@acceptance @project @search-v1
기능: 프로젝트 검색 V1 API

  @search @happy-path
  시나리오: 키워드로 프로젝트를 검색할 수 있다
    먼저 다음 프로젝트들이 등록되어 있다:
      | 프로젝트명           | 카테고리  |
      | AI 뉴스 요약 서비스  | AI_TECH  |
    만약 V1 API로 "뉴스" 키워드로 검색하면
    그러면 응답 코드가 "OK"이다
    그리고 검색 결과에 "AI 뉴스 요약 서비스"가 포함된다
```

### 5.2 Step 정의

```java
public class ProjectSearchV1Steps {

    @Autowired
    private TestContext context;

    @Autowired
    private TestApiSupport api;

    @만약("V1 API로 {string} 키워드로 검색하면")
    public void v1_api로_키워드로_검색하면(String keyword) {
        var response = api.projectSearch().searchV1(keyword, null, null);
        context.setResponse(response);
    }

    @그러면("응답 코드가 {string}이다")
    public void 응답_코드가_이다(String expectedCode) {
        String actualCode = context.getResponse().jsonPath().getString("code");
        assertThat(actualCode).isEqualTo(expectedCode);
    }
}
```

---

## 6. Fixture 클래스

```java
public class ProjectFixture {

    public static Project defaultProject(Member creator) {
        return Project.builder()
                .creator(creator)
                .name("테스트 프로젝트")
                .description("테스트 프로젝트 설명")
                .projectCategory(ProjectCategory.AI_TECH)
                .platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .isDeleted(false)
                .build();
    }

    public static Project withName(String name, Member creator) {
        return Project.builder()
                .creator(creator)
                .name(name)
                .description(name + " 설명")
                .projectCategory(ProjectCategory.AI_TECH)
                .platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .isDeleted(false)
                .build();
    }

    public static Project withCategory(ProjectCategory category, Member creator, String name) { ... }
    public static Project withPlatform(PlatformCategory platform, Member creator, String name) { ... }
    public static Project withRecruitment(Recruitment recruitment, Member creator, String name) { ... }
    public static Project withEndDate(LocalDate endDate, Member creator, String name) { ... }
}

public class MemberFixture {

    public static Member defaultMember() {
        return Member.createForTest("test@example.com", "테스트유저");
    }

    public static Member withName(String name) {
        return Member.createForTest(name.toLowerCase() + "@example.com", name);
    }

    public static Member withEmail(String email, String name) {
        return Member.createForTest(email, name);
    }
}
```

---

## 7. 네이밍 규칙

### 클래스 네이밍

| 테스트 유형 | 패턴 | 예시 |
|-----------|------|------|
| 단위 (Entity) | `{Entity}Test` | `ProjectTest` |
| 통합 (Service) | `{Service}IntegrationTest` | `ProjectQueryServiceIntegrationTest` |

### 메서드 네이밍

```java
// snake_case + 행위 설명
void returns_true_when_creator_id_matches()
void throws_exception_when_not_leader()
void returns_projects_when_keyword_matches_project_name()
```

### DisplayName

```java
@DisplayName("생성자의 ID와 일치하면 true를 반환한다")
@DisplayName("프로젝트명에 키워드가 포함된 프로젝트를 반환한다")
```

---

## 8. Checklist

**단위 테스트 (Entity)**
- [ ] 스프링 컨텍스트 없이 순수 Java
- [ ] `@Nested`로 메서드별 그룹화
- [ ] `@DisplayName` 한국어 설명
- [ ] 엔티티 행동 메서드 검증 (`is*`, `validate*`, `create`)
- [ ] 예외 케이스 검증

**통합 테스트 (Service)**
- [ ] `@SpringBootTest` + `@ActiveProfiles("test")`
- [ ] `@Transactional`로 테스트 격리
- [ ] `@Sql`로 시드 데이터 로드 (DELETE 포함)
- [ ] 상수로 시드 데이터 개수/키워드 관리
- [ ] 헬퍼 메서드로 중복 제거 (파일 맨 아래 배치)
- [ ] **반환 DTO 필드 검증**
- [ ] 목록 정렬 순서 검증
- [ ] 페이징 검증

**인수 테스트**
- [ ] Feature 파일 한국어 작성
- [ ] 시나리오별 태그 (`@happy-path`, `@edge-case`)

---

*Last Updated: 2026-03-31*
