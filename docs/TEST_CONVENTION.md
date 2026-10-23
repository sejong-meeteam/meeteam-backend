# MeeTeam Backend 테스트 컨벤션

## 1. 문서 목적과 기준

테스트 작성 기준, 실제 코드 위치, 보고서에 대응하는 검증 대상을 정리합니다.
이 문서의 기존 구현 현황은 GitHub `main`의 `28972a7` 기준입니다.
로컬에서 수정 중인 인증 시나리오·실행 설정은 아직 검증·커밋이 완료되지 않았으므로 완료 근거에 포함하지 않습니다.
**테스트 코드의 존재, 코드 검토, 신규 작성, 실행, 통과는 구분하여 기록합니다.**

## 2. 테스트 유형과 실제 대상

| 유형 | 목적 | 실제 코드 |
| --- | --- | --- |
| 인증 단위 테스트 | 계정 생성·보존, 잘못된 인증 거부, 토큰 발급·쿠키 처리, 활성화 조건 확인 | `global/auth/service/TestAccountServiceTest.java` |
| 로그인 흐름 단위 테스트 | 테스트 계정 분기와 일반 세종대 로그인 흐름의 분리 확인 | `global/auth/service/TestAccountSejongLoginTest.java` |
| 프로젝트 검색 통합 테스트 | Service → Repository → DB 연동과 반환 DTO 확인 | `integration/project/service/ProjectQueryServiceIntegrationTest.java` |
| Cucumber API 인수 테스트 | 사용자 시나리오에 따라 HTTP 요청과 저장 결과 확인 | `acceptance/cucumber/`와 `resources/features/` |

위 Java 경로의 기준은 `src/test/java/com/wardk/meeteam_backend/`입니다.
API 인수 테스트는 백엔드의 Controller → DB 흐름을 검증하는 E2E 성격을 가집니다.
브라우저 화면·프론트엔드까지 포함하는 E2E와는 범위가 다르며, 브라우저 자동화는 현재 확인된 백엔드 테스트 범위에 포함하지 않습니다.

## 3. 실제 디렉터리 구조

```text
src/test/java/com/wardk/meeteam_backend/
├── global/auth/service/          인증 관련 단위 테스트
├── integration/project/service/  프로젝트 검색 통합 테스트
├── acceptance/cucumber/
│   ├── CucumberTest.java         시나리오 실행기
│   ├── CucumberSpringConfiguration.java  Spring 서버·초기화 설정
│   ├── steps/                    시나리오 문장과 Java 동작 연결
│   ├── api/                      RestAssured HTTP 요청 헬퍼
│   ├── support/                  상태 공유·API/Repository 접근·DB 초기화
│   └── factory/                  테스트 회원 생성 헬퍼
├── fixture/                      공통 엔티티 생성 도우미
└── external/                     외부 포털 관련 테스트

src/test/resources/
├── features/                     한국어 시나리오 파일
├── sql/project-search-data.sql   검색 통합 테스트 데이터
├── data-h2.sql                   직군·직무·기술 스택 초기 데이터
└── application-test.yml          H2·Redis 등 테스트 설정
```

`unit/{도메인}/entity/`는 현재 존재하는 구조가 아닙니다.
문서 예시를 실제 클래스나 패키지의 존재 근거로 사용하지 않습니다.

## 4. 공통 작성 방식

- **Given / 먼저:** 회원·프로젝트·입력값 등 사전 조건을 준비합니다.
- **When / 만약:** 검증할 메서드 또는 API를 호출합니다.
- **Then / 그러면:** 기대 결과를 확인합니다.
- 정상 상황 외에 필수값 누락, 잘못된 인증, 권한 부족, 중복 요청 등의 조건을 기능에 맞춰 구분합니다.
- 상태 코드뿐 아니라 오류 코드, 응답 DTO, 쿠키, DB 상태 등 해당 테스트의 관찰 대상을 명시합니다.
- 실패 응답을 검사할 때 서버 내부 오류인 `500`을 정상적인 입력 거부로 간주하지 않습니다.
- 테스트 이름은 검증하는 행동과 조건을 나타냅니다. 기존 파일의 camelCase 또는 설명형 snake_case와 일관성을 유지합니다.
- `@Nested`와 `@DisplayName`은 조건별 그룹화와 결과 설명에 사용하며, 모든 테스트에 이미 적용됐다고 가정하지 않습니다.

### 단위 테스트의 실제 예시

`TestAccountServiceTest.wrongPasswordCannotIssueTokens()`는 잘못된 비밀번호를 거부하고 토큰 생성기를 호출하지 않는지 확인합니다.

```java
@Test
void wrongPasswordCannotIssueTokens() {
    Member member = Member.createForTest(TestAccountService.EMAIL, "Test", encoder.encode(password));
    when(members.findByEmailAndDeletedAtIsNull(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
    assertThrows(CustomException.class, () -> service.login(TestAccountService.USERNAME, "wrong"));
    verifyNoInteractions(jwt);
}
```

위 예시는 기존 클래스의 필드와 설정을 사용하는 메서드 발췌입니다.
Mockito로 의존성을 대체하며, 빈 등록 조건은 `ApplicationContextRunner`로 별도 확인합니다.

## 5. 테스트 데이터 컨벤션과 통합 테스트

`ProjectQueryServiceIntegrationTest`는 `@SpringBootTest`, `@ActiveProfiles("test")`, `@Transactional`, `@Sql("/sql/project-search-data.sql")`을 사용합니다.
검색 결과는 `Page`가 아니라 **`Slice<ProjectCardResponse>`**입니다.

- 프로젝트명·리더명 검색, 카테고리·플랫폼·모집 상태 필터를 확인합니다.
- 최신순·마감일순 정렬, 요청 크기와 `hasNext()`, DTO 필드 변환을 확인합니다.
- 실제 카테고리는 `CAPSTONE`, `CREATIVE_SEMESTER`, `CLUB`입니다. 예전 `AI_TECH`·`HEALTHCARE`를 현재 값으로 사용하지 않습니다.
- 시드 데이터의 ID·날짜·FK 관계·기대 개수를 함께 관리합니다. SQL 주석보다 실제 데이터와 테스트의 기대값을 대조합니다.
- 엔티티 생성에는 `MemberFixture`, `ProjectFixture`를 사용하고, 저장이 필요하면 Repository를 명시적으로 사용합니다.
- 반복 요청 생성·검색 호출은 기존 테스트의 `requestWithCategory()`, `defaultPageable()`, `search()` 같은 헬퍼로 구분합니다.
- HTTP 요청은 별도 서버 트랜잭션에서 처리되므로 테스트 메서드의 롤백만으로 모든 HTTP 시나리오가 격리된다고 가정하지 않습니다.

## 6. 시나리오 파일과 실행 코드의 연결

| 시나리오 파일 | 내용 | 관련 실행 코드 |
| --- | --- | --- |
| `auth.feature` | 인증 | `AuthSteps` → `AuthAPI` |
| `project-create.feature` | 프로젝트 생성 | `ProjectCreateSteps` → `ProjectCreateApi` |
| `project-application.feature` | 지원·승인·거절·팀원·알림 | `ProjectApplicationSteps` → 관련 API 헬퍼 |
| `member.feature`, `project-like.feature`, `project-management.feature`, `project-search.feature`, `qna.feature` | 회원·좋아요·관리·검색·Q&A | 전체 문장 연결 및 현재 API 호환 여부를 추가 확인해야 하는 범위 |

`src/test/resources/features/`에는 총 8개 feature 파일이 있습니다.
하지만 직접적인 Step 클래스는 인증·프로젝트 생성·프로젝트 지원의 3개에 집중돼 있습니다.
**feature 파일이나 API 헬퍼가 있다는 사실만으로 모든 문장이 구현됐거나 실행에 성공했다고 판단하지 않습니다.**

예를 들어 프로젝트 지원 흐름은 다음과 같습니다.

```text
project-application.feature의 지원 요청 문장
→ ProjectApplicationSteps의 @만약 메서드
→ ProjectApplicationApi의 지원 요청
→ POST /api/v1/projects/{projectId}/application
→ 응답·지원 상태·팀원 또는 알림 저장 결과 확인
```

알림 조회로 저장 결과를 확인하는 것과 실제 SSE 스트림 수신을 검증하는 것은 별개입니다.

## 7. 현재 확인된 불일치와 실행 전 주의사항

다음은 게시된 기준 코드에서 발견한 보완 항목이며, 수정·통과 완료 목록이 아닙니다.

- 서비스는 세종대 포털 로그인·가입을 사용하지만, 기존 `AuthAPI`와 `auth.feature`에는 제거된 이메일·OAuth 인증 흐름이 남아 있습니다.
- 현재 인증 경로는 `/api/v1/auth/login/sejong`, `/api/v1/auth/register/sejong`이며, JWT는 HttpOnly 쿠키로 전달됩니다. 기존 테스트의 응답 본문·헤더 가정도 함께 대조해야 합니다.
- 기본 Gradle `test`와 `integrationTest`는 Cucumber 엔진을 제외합니다. 전용 실행 태스크와 엔진·suite 선택을 정리한 뒤 실제 실행해야 합니다.
- Cucumber 7.31.0과 기존 JUnit Platform 1.11 계열 사이에서 엔진 발견 오류가 확인됐습니다. 의존성 정합성을 맞춘 후 재검증해야 합니다.
- 기준 `application-test.yml`은 H2 메모리 DB와 Redis `localhost:6379`를 지정합니다. 이 설정만으로 전용 Redis나 외부 서비스 격리가 보장되지 않습니다.
- `external/`의 포털 테스트와 애플리케이션 워밍업은 외부 호출 가능성이 있으므로, 오프라인 테스트에서 무조건 실행하지 않습니다.
- 테스트 실행 전에는 전용 DB·Redis, 포털·S3 대역, 워밍업 비활성화 등 격리 조건을 확인합니다. 운영 데이터에 초기화·삭제 명령을 실행하지 않습니다.
- 실제 계정·비밀번호·키를 테스트 데이터나 문서에 넣지 않습니다. 토큰·쿠키·인증 요청 전체를 로그에 남기지 않습니다.

로컬에서 진행한 수정과 실행 시도는 아직 검증·커밋 완료 상태가 아닙니다.
이 문서 수정만으로 위 오류가 해결되거나 전체 시나리오가 통과하는 것은 아닙니다.

## 8. 인증·알림 책임 분리와 보완 기준

다음은 기존 코드에 존재하는 구조입니다. 이번 주 신규 리팩토링 완료를 의미하지 않습니다.

| 구성 요소 | 기존 역할 | 테스트 보완 시 확인할 내용 |
| --- | --- | --- |
| `AuthService` / `SejongPortalClient` | 가입·로그인 업무와 외부 포털 통신 분리 | 기존/신규 회원 분기, 인증 실패, 일회용 코드 소비, 외부 장애 조건 |
| `NotificationPayloadFactory` / `NotificationPayloadStrategy` | 알림 유형에 맞는 응답 데이터 생성 분리 | 유형별 전략 선택, 미지원 유형, 데이터 변환 |
| `SseEmitterSender` | 전송과 실패 연결 정리를 공통 처리 | 전송 성공·예외, 연결 제거, 다른 연결에 대한 영향 |

새 변경을 리팩토링 성과로 기록하려면 수정 파일·변경 이유와 변경 전후의 테스트 결과를 남깁니다.
기존 분리 구조를 읽고 분석한 활동은 구조 분석으로 기록하며, 직접 수정한 것과 구분합니다.

## 9. 보고서와 실행 결과 기록

| 내용 | 근거 위치 | 구분 |
| --- | --- | --- |
| 인증 단위 테스트 점검 | `global/auth/service/`의 두 테스트 클래스 | 기존 코드 점검 |
| 프로젝트 검색 통합 테스트 점검 | `integration/project/service/ProjectQueryServiceIntegrationTest.java`, 검색 SQL | 기존 코드 점검 |
| 사용자 시나리오 구성과 검증 범위 정리 | `resources/features/`와 `acceptance/cucumber/steps/` | 시나리오·연결 구조 정리 |
| 공통 데이터·헬퍼 사용 방식 정리 | `fixture/`, Cucumber의 `support/`·`api/`, 통합 테스트 내부 헬퍼 | 작성 방식 정리 |
| 인증·알림 역할과 개선점 정리 | 8절에 제시한 기존 클래스 | 구조 분석·보완 항목 도출 |

실제로 테스트를 실행했으면 **명령, 환경, 대상, 실행·통과·실패·건너뜀 수, 미검증 항목**을 기록합니다.

