# Cucumber ATDD 테스트 컨벤션

이 문서는 Cucumber 기반 ATDD(Acceptance Test-Driven Development) 테스트 구조의 컨벤션을 정의합니다.

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [TestContext](#2-testcontext)
3. [Step 클래스](#3-step-클래스)
4. [API 클래스](#4-api-클래스)
5. [Facade 패턴](#5-facade-패턴)
6. [Factory 패턴](#6-factory-패턴)
7. [Hooks vs Background](#7-hooks-vs-background)
8. [DatabaseCleaner](#8-databasecleaner)
9. [상수 관리](#9-상수-관리)
10. [Feature 파일 작성](#10-feature-파일-작성)
11. [디렉토리 구조](#11-디렉토리-구조)

---

## 1. 아키텍처 개요

### 전체 흐름

```
Feature 파일
    │
    ├─ Background (비즈니스 전제 조건)
    └─ Scenario
        │
        ├─ Given Step ──→ Factory/Repository로 데이터 생성 ──→ TestContext 저장
        │
        ├─ When Step ──→ TestApiSupport → API 클래스 ──→ TestContext에 응답 저장
        │
        └─ Then Step ──→ TestContext에서 응답 검증 + Repository로 DB 검증
```

### 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **Step은 Context에 의존** | Step 클래스는 TestContext를 통해 데이터를 공유한다 |
| **API 요청은 분리** | HTTP 요청 로직은 별도 API 클래스로 분리한다 |
| **Facade로 접근** | API와 Repository는 Facade를 통해 접근한다 |
| **한글 메서드명** | 도메인 언어를 반영한 한글 메서드명을 사용한다 |
| **기술/비즈니스 분리** | Hooks는 기술적 초기화, Background는 비즈니스 전제 조건 |

---

## 2. TestContext

### 역할

- 시나리오 내 Step 클래스 간 데이터 공유를 위한 **중앙 저장소**
- `@ScenarioScope`로 시나리오마다 새로운 인스턴스 생성

### 구현 규칙

```java
@Component
@ScenarioScope
public class TestContext {

    // 1. 공통 필드: 모든 시나리오에서 사용하는 데이터
    private String accessToken;
    private ExtractableResponse<Response> response;

    // 2. 도메인별 데이터 클래스: 내부 클래스로 정의
    @Getter @Setter
    public static class EntityData {
        private Long id;
        private String name;
        // 필요한 필드 추가
    }

    // 3. 도메인 데이터 인스턴스
    private final EntityData entityData = new EntityData();

    // 4. Getter/Setter
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public ExtractableResponse<Response> getResponse() { return response; }
    public void setResponse(ExtractableResponse<Response> response) { this.response = response; }

    public EntityData entity() { return entityData; }
}
```

### 사용 예시

```java
// Given Step에서 저장
context.entity().setId(createdEntity.getId());

// When Step에서 토큰 사용
api.entity().생성(context.getAccessToken(), request);

// Then Step에서 검증
assertThat(context.getResponse().statusCode()).isEqualTo(200);
```

### 규칙

1. **공통 필드**: `accessToken`, `response`는 항상 포함
2. **도메인 데이터**: 내부 클래스(`XxxData`)로 그룹화
3. **메서드 네이밍**: 도메인 데이터는 `entity()` 형태의 메서드로 접근
4. **불변성**: 객체 자체는 final, 내부 필드만 변경

---

## 3. Step 클래스

### 역할

- Gherkin 문법(Given/When/Then)을 Java 코드와 연결
- **비즈니스 로직 없이** API 호출 또는 검증만 수행

### 구현 규칙

```java
public class EntitySteps {

    @Autowired
    private TestContext context;

    @Autowired
    private TestApiSupport api;

    @Autowired
    private TestRepositorySupport repository;

    @Autowired
    private EntityFactory factory;

    // === Given: 데이터 준비 ===
    @Given("{string} 엔티티가 등록되어 있다")
    public void 엔티티가_등록되어_있다(String name) {
        Entity entity = factory.create(name);
        context.entity().setId(entity.getId());
        context.entity().setName(name);
    }

    // === When: API 호출 ===
    @When("관리자가 해당 엔티티를 조회한다")
    public void 관리자가_해당_엔티티를_조회한다() {
        var response = api.entity().조회(
            context.getAccessToken(),
            context.entity().getId()
        );
        context.setResponse(response);
    }

    // === Then: 검증 ===
    @Then("엔티티 조회가 성공한다")
    public void 엔티티_조회가_성공한다() {
        assertThat(context.getResponse().statusCode()).isEqualTo(200);
    }

    @Then("엔티티의 이름이 {string}이다")
    public void 엔티티의_이름이_이다(String expectedName) {
        String actualName = context.getResponse().jsonPath().getString("name");
        assertThat(actualName).isEqualTo(expectedName);
    }

    // === Helper: 반복 로직 추출 ===
    private void assertErrorResponse(int expectedStatus, String expectedMessage) {
        assertThat(context.getResponse().statusCode()).isEqualTo(expectedStatus);
        assertThat(context.getResponse().jsonPath().getString("message"))
            .contains(expectedMessage);
    }
}
```

### 규칙

1. **의존성**: `TestContext`, `TestApiSupport`, `TestRepositorySupport`, `Factory` 주입
2. **Given**: Factory 또는 Repository로 데이터 생성 → Context에 저장
3. **When**: API 호출 → 응답을 Context에 저장
4. **Then**: Context의 응답 또는 Repository로 검증
5. **Helper 메서드**: 반복되는 검증 로직은 private 메서드로 추출

### Step 분리 기준

```
steps/
├── AuthSteps.java         # 인증 관련 (로그인, 로그아웃)
├── EntityASteps.java      # 도메인 A 관련
├── EntityBSteps.java      # 도메인 B 관련
└── CommonSteps.java       # 공통 Step (필요시)
```

- **도메인 단위**로 Step 클래스 분리
- 하나의 Step 클래스는 **100줄 이하** 권장

---

## 4. API 클래스

### 역할

- HTTP 요청 로직을 캡슐화
- RestAssured 기반 BDD 스타일 구현
- **요청 데이터의 기본값 설정 및 변환 처리**

### 구현 규칙

```java
@Component
public class EntityAPI {

    // === API 요청 메서드 ===

    // 한글 메서드명으로 도메인 언어 표현
    public ExtractableResponse<Response> 생성(String token, String name, String category) {
        Map<String, Object> params = 생성_기본_요청(name, category);
        return 생성_요청(token, params);
    }

    public ExtractableResponse<Response> 조회(String token, Long id) {
        return RestAssured.given().log().all()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/entities/{id}", id)
            .then().log().all()
            .extract();
    }

    // === 요청 데이터 생성 (기본값 포함) ===

    /**
     * 기본 요청 데이터 생성 - 필수값 외 기본값 자동 설정
     */
    public Map<String, Object> 생성_기본_요청(String name, String category) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("category", category);
        params.put("description", "테스트 설명");           // 기본값
        params.put("type", "DEFAULT");                      // 기본값
        params.put("status", "ACTIVE");                     // 기본값
        return params;
    }

    /**
     * 특정 필드 누락 요청 (Validation 테스트용)
     */
    public Map<String, Object> 이름_누락_요청() {
        Map<String, Object> params = 생성_기본_요청("", "CATEGORY");
        params.remove("name");  // 필드 제거
        return params;
    }

    // === 내부 헬퍼 ===

    private ExtractableResponse<Response> 생성_요청(String token, Map<String, Object> params) {
        var spec = RestAssured.given().log().all()
            .contentType(ContentType.JSON)
            .body(params);

        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }

        return spec
            .when()
            .post("/api/entities")
            .then().log().all()
            .extract();
    }
}
```

### 규칙

1. **한글 메서드명**: `생성()`, `조회()`, `수정()`, `삭제()` 등
2. **로깅**: `log().all()`로 요청/응답 로깅
3. **반환 타입**: `ExtractableResponse<Response>`
4. **기본값 처리**: API 클래스에서 요청 데이터의 기본값 설정
   - Step 클래스는 비즈니스적으로 의미 있는 값만 전달
   - 나머지 필드는 API 클래스에서 기본값 자동 설정
5. **Validation 테스트**: 필드 누락/잘못된 값 요청 메서드 제공
6. **토큰 처리**: null 체크하여 비인증 요청도 지원

---

## 5. Facade 패턴

### TestApiSupport (API Facade)

```java
@Component
public class TestApiSupport {

    @Autowired private AuthAPI authAPI;
    @Autowired private EntityAAPI entityAAPI;
    @Autowired private EntityBAPI entityBAPI;

    public AuthAPI auth() { return authAPI; }
    public EntityAAPI entityA() { return entityAAPI; }
    public EntityBAPI entityB() { return entityBAPI; }
}
```

### TestRepositorySupport (Repository Facade)

```java
@Component
public class TestRepositorySupport {

    @Autowired private EntityARepository entityARepository;
    @Autowired private EntityBRepository entityBRepository;

    public EntityARepository entityA() { return entityARepository; }
    public EntityBRepository entityB() { return entityBRepository; }
}
```

### 사용 예시

```java
// Step 클래스에서
@Autowired private TestApiSupport api;
@Autowired private TestRepositorySupport repository;

// API 호출
api.entityA().생성(token, params);

// Repository 접근
Entity entity = repository.entityA().findById(id).orElseThrow();
```

### 규칙

1. **통합 진입점**: 모든 API/Repository를 하나의 Facade로 접근
2. **메서드 네이밍**: `도메인명()` 형태로 반환
3. **확장성**: 새 도메인 추가 시 Facade에 메서드만 추가

---

## 6. Factory 패턴

### 역할

- Given 단계에서 테스트 데이터 생성
- 기본값 자동 설정으로 테스트 코드 간소화

### 구현 규칙

```java
@Component
public class EntityFactory {

    @Autowired
    private EntityRepository entityRepository;

    public Entity create(String name) {
        return create(name, "DEFAULT_TYPE");
    }

    public Entity create(String name, String type) {
        Entity entity = Entity.builder()
            .name(name)
            .type(type)
            .createdAt(LocalDateTime.now())  // 기본값 설정
            .build();

        return entityRepository.save(entity);
    }

    public Entity createWithStatus(String name, String status) {
        Entity entity = Entity.builder()
            .name(name)
            .status(EntityStatus.valueOf(status))
            .createdAt(LocalDateTime.now())
            .build();

        return entityRepository.save(entity);
    }
}
```

### 규칙

1. **오버로딩**: 필수 파라미터만 받는 메서드 + 상세 파라미터 메서드
2. **기본값**: 테스트에 불필요한 필드는 기본값 자동 설정
3. **Builder 패턴**: 가독성을 위해 Builder 사용
4. **즉시 저장**: 생성 후 바로 Repository에 저장하여 반환

---

## 7. Hooks vs Background

### Hooks (CucumberSpringConfiguration)

**기술적 초기화**에 사용

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {

    @LocalServerPort
    private int port;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Before
    public void setUp() {
        RestAssured.port = port;      // 포트 설정
        databaseCleaner.clear();       // DB 초기화
    }
}
```

### Background (Feature 파일)

**비즈니스 전제 조건**에 사용

```gherkin
Feature: 엔티티 관리

  Background:
    Given 관리자로 로그인되어 있다
    And 기본 데이터가 등록되어 있다
```

### 역할 분담

| 구분 | Hooks (@Before) | Background |
|------|-----------------|------------|
| **목적** | 기술적 초기화 | 비즈니스 전제 조건 |
| **위치** | Java 코드 | Feature 파일 |
| **적용 범위** | 모든 시나리오 | 해당 Feature 내 시나리오 |
| **예시** | DB 초기화, 포트 설정 | 로그인, 기본 데이터 생성 |
| **가시성** | 개발자만 확인 | 비개발자도 확인 가능 |

### 규칙

1. **Hooks**: DB 초기화, RestAssured 설정 등 **프레임워크 레벨** 작업
2. **Background**: 로그인, 데이터 준비 등 **비즈니스 레벨** 전제 조건
3. **Background 재사용**: 여러 시나리오에서 공통으로 필요한 전제 조건

---

## 8. DatabaseCleaner

### 역할

- 시나리오 간 데이터 격리
- 모든 테이블 자동 감지 및 TRUNCATE

### 구현 규칙

```java
@Component
public class DatabaseCleaner {

    @Autowired
    private EntityManager entityManager;

    private List<String> tableNames;

    @PostConstruct
    public void init() {
        tableNames = entityManager.getMetamodel().getEntities().stream()
            .map(this::extractTableName)
            .toList();
    }

    private String extractTableName(EntityType<?> entity) {
        Table tableAnnotation = entity.getJavaType().getAnnotation(Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            return tableAnnotation.name();
        }
        return toSnakeCase(entity.getName());
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    @Transactional
    public void clear() {
        entityManager.flush();
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        for (String tableName : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
        }

        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
```

### 규칙

1. **자동 감지**: EntityManager의 metamodel에서 테이블명 추출
2. **외래키 처리**: REFERENTIAL_INTEGRITY 비활성화 후 복구
3. **@Transactional**: 트랜잭션 내에서 실행
4. **@PostConstruct**: 애플리케이션 시작 시 테이블 목록 캐싱

---

## 9. 상수 관리

### 역할

- 매직 스트링 제거
- 오류 메시지 일관성 보장

### 구현 규칙

```java
public class TestConstants {

    private TestConstants() {} // 인스턴스화 방지

    // === 상태값 ===
    public static final String 상태_활성 = "ACTIVE";
    public static final String 상태_비활성 = "INACTIVE";
    public static final String 상태_삭제 = "DELETED";

    // === 오류 메시지 ===
    public static final String 오류_권한없음 = "권한이 없습니다";
    public static final String 오류_찾을수없음 = "찾을 수 없습니다";
    public static final String 오류_중복 = "이미 존재합니다";

    // === 테스트 데이터 ===
    public static final String 기본_이름 = "테스트";
    public static final String 기본_이메일 = "test@example.com";
}
```

### 사용 예시

```java
import static com.example.steps.constant.TestConstants.*;

@Then("오류 메시지가 표시된다")
public void 오류_메시지가_표시된다() {
    assertErrorResponse(404, 오류_찾을수없음);
}
```

### 규칙

1. **한글 상수명**: 가독성을 위해 한글 사용
2. **그룹화**: 상태값, 오류 메시지, 테스트 데이터로 구분
3. **static import**: Step 클래스에서 static import로 사용
4. **private 생성자**: 유틸리티 클래스로 인스턴스화 방지

---

## 10. Feature 파일 작성

### 구조

```gherkin
# language: ko
Feature: 기능명
  기능에 대한 설명

  Background:
    Given 공통 전제 조건

  Scenario: 정상 케이스 - 설명
    Given 전제 조건
    When 행위
    Then 기대 결과

  Scenario: 예외 케이스 - 설명
    Given 전제 조건
    When 잘못된 행위
    Then 오류 응답
```

### 규칙

1. **시나리오 분류**: 정상 케이스, 예외 케이스, 엣지 케이스로 구분
2. **시나리오 네이밍**: `정상 케이스 - 설명` 또는 `예외 케이스 - 설명` 형식
3. **파라미터**: `{string}`, `{int}` 등 Cucumber Expression 사용
4. **DataTable**: 복잡한 데이터는 테이블 형식 활용

### DataTable 사용 예시

```gherkin
Given 다음과 같은 데이터가 등록되어 있다:
  | name   | email               | type     |
  | 홍길동 | hong@example.com    | NORMAL   |
  | 김철수 | kim@example.com     | PREMIUM  |
```

```java
@Given("다음과 같은 데이터가 등록되어 있다:")
public void 다음과_같은_데이터가_등록되어_있다(DataTable dataTable) {
    List<Map<String, String>> rows = dataTable.asMaps();
    for (Map<String, String> row : rows) {
        factory.create(row.get("name"), row.get("email"), row.get("type"));
    }
}
```

---

## 11. 디렉토리 구조

```
src/test/
├── java/com/example/
│   ├── CucumberTestRunner.java          # Cucumber 실행 진입점
│   │
│   ├── steps/                           # Step 정의
│   │   ├── AuthSteps.java
│   │   ├── EntityASteps.java
│   │   ├── EntityBSteps.java
│   │   ├── CucumberSpringConfiguration.java  # Hooks
│   │   └── constant/
│   │       └── TestConstants.java       # 상수
│   │
│   ├── api/                             # API 클래스 + Context
│   │   ├── TestContext.java
│   │   ├── AuthAPI.java
│   │   ├── EntityAAPI.java
│   │   └── EntityBAPI.java
│   │
│   ├── support/                         # 지원 클래스
│   │   ├── TestApiSupport.java          # API Facade
│   │   ├── TestRepositorySupport.java   # Repository Facade
│   │   └── DatabaseCleaner.java
│   │
│   └── factory/                         # Factory
│       ├── EntityAFactory.java
│       └── EntityBFactory.java
│
└── resources/
    └── features/                        # Feature 파일
        ├── entity_a.feature
        └── entity_b.feature
```

---

## 체크리스트

새 도메인 추가 시 확인 항목:

- [ ] `TestContext`에 도메인 데이터 클래스 추가
- [ ] `EntityAPI` 클래스 생성
- [ ] `TestApiSupport`에 API 메서드 추가
- [ ] `TestRepositorySupport`에 Repository 메서드 추가
- [ ] `EntityFactory` 클래스 생성
- [ ] `EntitySteps` 클래스 생성
- [ ] `TestConstants`에 관련 상수 추가
- [ ] Feature 파일 작성

---

## 참고

- [Cucumber 공식 문서](https://cucumber.io/docs)
- [RestAssured 가이드](https://rest-assured.io/)
- [Spring Boot 테스트](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)