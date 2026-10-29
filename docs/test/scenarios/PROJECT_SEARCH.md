# 프로젝트 검색 테스트 시나리오

서비스와 DB를 연결해 검색 조건·정렬·결과 데이터를 확인합니다. `ProjectQueryServiceIntegrationTest`의 **통합 테스트 21개**를 정리했습니다. 화면·HTTP 요청·운영 MySQL을 검사하는 테스트는 아닙니다.

기준 코드는 `b57d461`이며 로컬 미완료 수정은 제외했습니다. 테스트는 아직 실행하지 않았습니다.

[실행 방법](../README.md) · [결과 양식](../results/TEMPLATE.md)

## 1. 용어

| 용어 | 설명 |
| --- | --- |
| 통합 테스트 | 여러 부품을 연결해 확인하는 자동 검사 |
| 시드 SQL | 같은 조건을 재현하기 위한 회원·프로젝트 데이터 |
| Fixture | 테스트용 객체를 만드는 도우미. 저장은 별도 |
| Repository | DB에 저장하고 조회하는 통로 |
| DTO | 검색 결과를 묶어 반환하는 데이터 |
| Slice / hasNext | 이번 결과 묶음과 다음 묶음의 존재 여부. 전체 페이지 수는 제공하지 않음 |
| null | 값이 없음. 빈 문자열과는 다름 |
| 롤백 | 테스트에서 변경한 DB 내용을 되돌리는 처리 |

개발자가 실행하고 검토자는 아래 IT 번호와 보고서를 대조합니다. 사람이 DB를 직접 수정하거나 화면에서 따라 하는 절차는 아닙니다.

## 2. 준비 데이터와 함수

[검색 시드 SQL](../../../src/test/resources/sql/project-search-data.sql)의 실제 입력 행을 기준으로 합니다. SQL의 예전 개수 주석은 일부 맞지 않습니다.

| 구분 | 준비 데이터 |
| --- | --- |
| 회원 / 프로젝트 | 5명 / 20개(ID 1~20) |
| CAPSTONE / CREATIVE_SEMESTER / CLUB | 10개 / 4개 / 6개 |
| WEB / IOS / ANDROID | 11개 / 4개 / 5개 |
| RECRUITING / CLOSED / SUSPENDED | 16개 / 2개 / 2개 |
| 이름에 AI 포함 | ID 1,2,3. IT-01이 정확한 개수를 검사하는 것은 아님 |
| 리더 홍길동 | 프로젝트 ID 1,4,11,16. IT-02는 개수 자체를 검사하지 않음 |

날짜는 정렬용 고정 데이터입니다. 과거 날짜나 ‘마감 3일 전’ 같은 설명을 현재 모집 가능 기간으로 해석하지 않습니다.

- `@Sql`로 데이터를 준비하고 `searchV1`을 호출합니다. 시드에 삭제문이 있으므로 수동 실행하지 않습니다.
- `search()`는 사용자 정보를 null로 전달하므로 모두 비로그인 조건입니다.
- `requestWithKeyword/Category/Recruitment/Platform/Sort()`로 필요한 검색 조건을 만듭니다. `emptyRequest()`의 정렬 기본값은 LATEST입니다.
- `defaultPageable()`은 첫 묶음 20개, `pageableOf(page,size)`는 지정한 묶음을 조회합니다. 0번이 첫 번째이며 UI 표시 개수를 뜻하지는 않습니다.
- 동적 데이터는 `MemberFixture.withName()`·`ProjectFixture.withName()`으로 만들고 Repository로 저장합니다. 등록 API 검증은 아닙니다.
- 테스트 트랜잭션은 기본 롤백되지만 클래스 실행 전 SQL까지 같은 범위에서 되돌려진다고 가정하지 않습니다.

## 3. 실행 전 확인

공통 준비·명령은 [실행 안내](../README.md)의 3~5절을 따릅니다. 아래 조건을 확인하지 못하면 실행을 멈춥니다.

- 테스트 자원의 H2 메모리 DB가 적용돼야 합니다. 서버용 MySQL 설정과 혼동하지 않고 환경변수 재정의도 확인합니다. `create-drop`과 시드 삭제문이 있어 운영·공유 DB에서는 실행하면 안 됩니다.
- 테스트 설정은 `localhost:6379`를 사용합니다. 소유자가 확인된 전용 Redis를 준비하고 주소·포트를 맞춥니다. 기존 Redis는 재사용·중지·초기화하지 않습니다.
- Redis·WireMock 의존성만으로 외부 서비스가 자동 대체되지는 않습니다. H2의 MySQL 모드도 실제 MySQL 검증을 대신하지 않습니다.
- 공통 명령의 `APP_WARMUP_ENABLED=false`, `TEST_ACCOUNT_ENABLED=false`를 적용합니다. 워밍업은 다른 로컬 서버나 포털에 로그인 요청을 보낼 수 있습니다.
- 테스트 AWS 값과 S3 비활성화 값만으로 통신 차단이 보장되지 않습니다. 실 AWS 키를 넣지 말고 외부 호출을 차단·격리합니다.

## 4. 시나리오

기본 조건은 공통 데이터, 첫 묶음 20개, 최신순, 비로그인 검색입니다. 아래 기대 결과는 코드에서 실제 확인하는 범위만 적었습니다.

### IT-01. 프로젝트 이름으로 AI 검색

- **준비:** 시드의 이름에 AI가 들어간 프로젝트 ID 1,2,3.
- **절차:** 1. `requestWithKeyword("AI")` 생성. 2. `search()` 호출. 3. 내용의 이름 검사.
- **기대 결과:** 결과가 비어 있지 않고, 반환된 모든 프로젝트명에 `AI`가 포함됩니다. **정확히 3개인지는 검증하지 않습니다.**
- **코드:** `returns_projects_when_keyword_matches_project_name`

### IT-02. 리더 이름으로 홍길동 검색

- **준비:** 홍길동 회원과 그가 리더인 시드 프로젝트.
- **절차:** 1. `requestWithKeyword("홍길동")` 생성. 2. 검색. 3. 모든 리더명 검사.
- **기대 결과:** 결과가 비어 있지 않고 모든 `creatorName`이 정확히 `홍길동`입니다. 개수는 단언하지 않습니다.
- **코드:** `returns_projects_when_keyword_matches_leader_name`

### IT-03. 없는 검색어는 빈 결과

- **준비:** 시드에 없는 검색어 `존재하지않는키워드XYZ`.
- **절차:** 1. 해당 키워드 요청 생성. 2. 검색. 3. 내용과 다음 묶음 표시 검사.
- **기대 결과:** 내용이 비어 있고 `hasNext()`가 false입니다. 오류 화면이나 HTTP 상태 검증은 아닙니다.
- **코드:** `returns_empty_slice_when_no_results`

### IT-04. 캡스톤 분류로 찾기

- **준비:** CAPSTONE 시드 10개.
- **절차:** 1. `requestWithCategory(CAPSTONE)` 생성. 2. 검색. 3. 수와 분류 코드 검사.
- **기대 결과:** 10개이며 모든 `categoryCode`가 `CAPSTONE`입니다.
- **코드:** `returns_only_capstone_projects`

### IT-05. 창의학기제 분류로 찾기

- **준비:** CREATIVE_SEMESTER 시드 4개.
- **절차:** 1. `requestWithCategory(CREATIVE_SEMESTER)` 생성. 2. 검색. 3. 수와 분류 코드 검사.
- **기대 결과:** 4개이며 모든 `categoryCode`가 `CREATIVE_SEMESTER`입니다.
- **코드:** `returns_only_creative_semester_projects`

### IT-06. iOS 플랫폼으로 찾기

- **준비:** IOS 시드 4개.
- **절차:** 1. `requestWithPlatform(IOS)` 생성. 2. 검색. 3. 수와 플랫폼명 검사.
- **기대 결과:** 4개이며 모든 `platformName`이 `IOS`입니다.
- **코드:** `returns_only_ios_projects`

### IT-07. Android 플랫폼으로 찾기

- **준비:** ANDROID 시드 5개.
- **절차:** 1. `requestWithPlatform(ANDROID)` 생성. 2. 검색. 3. 수와 플랫폼명 검사.
- **기대 결과:** 5개이며 모든 `platformName`이 `ANDROID`입니다.
- **코드:** `returns_only_android_projects`

### IT-08. 모집 중 조건의 결과 수

- **준비:** RECRUITING 상태 시드 16개.
- **절차:** 1. `requestWithRecruitment(RECRUITING)` 생성. 2. 검색. 3. 결과 수 검사.
- **기대 결과:** 16개입니다. 각 항목의 실제 모집 상태 값이나 오늘 모집 가능한지는 검증하지 않습니다.
- **코드:** `returns_only_recruiting_projects`

### IT-09. 모집 종료 조건의 결과 수

- **준비:** CLOSED 상태 시드 2개.
- **절차:** 1. `requestWithRecruitment(CLOSED)` 생성. 2. 검색. 3. 결과 수 검사.
- **기대 결과:** 2개입니다. 각 항목의 상태 값은 직접 단언하지 않습니다.
- **코드:** `returns_only_closed_projects`

### IT-10. 모집 중단 조건의 결과 수

- **준비:** SUSPENDED 상태 시드 2개.
- **절차:** 1. `requestWithRecruitment(SUSPENDED)` 생성. 2. 검색. 3. 결과 수 검사.
- **기대 결과:** 2개입니다. 각 항목의 상태 값은 직접 단언하지 않습니다.
- **코드:** `returns_only_suspended_projects`

### IT-11. 마감일 정렬의 양끝 비교

- **준비:** 서로 다른 고정 마감일을 가진 시드. 현재 날짜와의 차이는 판정 조건이 아님.
- **절차:** 1. `requestWithSort(DEADLINE)` 생성. 2. 검색. 3. 첫 항목과 마지막 항목의 `endDate` 비교.
- **기대 결과:** 결과가 비어 있지 않고 첫 항목의 마감일이 마지막 항목보다 앞섭니다. **중간 항목 전체 순서, 특정 프로젝트가 첫 번째인지, 전체 개수는 단언하지 않습니다.**
- **코드:** `returns_projects_sorted_by_deadline_ascending`

### IT-12. 최신순 정렬의 양끝 비교

- **준비:** 시드에서 높은 ID일수록 생성일이 늦음.
- **절차:** 1. `requestWithSort(LATEST)` 생성. 2. 검색. 3. 첫 항목과 마지막 항목 ID 비교.
- **기대 결과:** 결과가 비어 있지 않고 첫 `projectId`가 마지막 `projectId`보다 큽니다. 생성일 직접 비교나 모든 인접 항목의 정렬은 검증하지 않습니다.
- **코드:** `returns_projects_sorted_by_latest`

### IT-13. 기본 크기 20으로 전체 시드 조회

- **준비:** 프로젝트 20개와 `emptyRequest()`, 기본 `PageRequest.of(0,20)`.
- **절차:** 1. 조건 없는 요청 생성. 2. 기본 검색. 3. 내용 수와 `hasNext()` 검사.
- **기대 결과:** 이번 내용이 20개이고 `hasNext()`는 false입니다. 전체 페이지 수를 검사하는 Page 테스트가 아닙니다.
- **코드:** `returns_page_size_results`

### IT-14. 첫 묶음을 5개만 조회

- **준비:** 시드 20개, `emptyRequest()`, `pageableOf(0,5)`.
- **절차:** 1. 조건 없는 요청과 0번·크기 5 설정 생성. 2. 해당 설정으로 검색. 3. 수와 다음 묶음 표시 검사.
- **기대 결과:** 5개이고 `hasNext()`는 true입니다. 다음 묶음으로 실제 이동하는 동작은 이 사례에 없습니다.
- **코드:** `returns_custom_page_size_results`

### IT-15. 마지막 묶음에는 다음 결과 없음

- **준비:** 시드 20개, `emptyRequest()`, `pageableOf(3,5)`로 네 번째 묶음 지정.
- **절차:** 1. 3번·크기 5 설정 생성. 2. 검색. 3. 수와 다음 묶음 표시 검사.
- **기대 결과:** 5개이고 `hasNext()`는 false입니다. 앞 묶음과의 중복 여부는 단언하지 않습니다.
- **코드:** `returns_false_has_next_on_last_page`

### IT-16. 캡스톤이면서 웹인 프로젝트 찾기

- **준비:** 시드에 CAPSTONE과 WEB을 함께 만족하는 프로젝트가 있음.
- **절차:** 1. `ProjectSearchRequest(null, CAPSTONE, null, WEB, null, null)` 생성. 2. 검색. 3. 모든 항목의 분류·플랫폼 검사.
- **기대 결과:** 결과가 비어 있지 않고 모든 항목의 `categoryCode=CAPSTONE`, `platformName=WEB`입니다. 정확한 수는 검증하지 않습니다.
- **코드:** `filters_by_category_and_platform`

### IT-17. 개발 검색어와 모집 중 조건 함께 전달

- **준비:** 이름에 `개발`이 들어가고 RECRUITING 상태인 시드.
- **절차:** 1. `ProjectSearchRequest("개발", null, RECRUITING, null, null, null)` 생성. 2. 검색. 3. 이름 검사.
- **기대 결과:** 결과가 비어 있지 않고 모든 프로젝트명에 `개발`이 포함됩니다. **모집 상태는 요청에 있지만 결과의 상태 값이나 개수를 단언하지 않으므로 모집 조건까지 검증됐다고 확대하지 않습니다.**
- **코드:** `filters_by_keyword_and_recruitment`

### IT-18. 비로그인 검색의 좋아요 표시

- **준비:** 시드, 조건 없는 요청, 사용자 정보 null.
- **절차:** 1. `emptyRequest()` 생성. 2. 비로그인 검색. 3. 반환 항목의 `isLiked` 검사.
- **기대 결과:** 반환된 모든 항목의 `isLiked`가 false입니다. 이 메서드에는 결과가 비어 있지 않다는 별도 단언이 없어, 빈 결과도 이 검증문만으로는 거부하지 못합니다. 로그인 사용자의 좋아요 저장·취소는 다루지 않습니다.
- **코드:** `is_liked_false_for_anonymous_user`

### IT-19. 캡스톤의 한글 표시명

- **준비:** CAPSTONE 시드와 enum의 `getDisplayName()` 값 `캡스톤`.
- **절차:** 1. CAPSTONE 요청 생성. 2. 검색. 3. 첫 항목의 `categoryName` 검사.
- **기대 결과:** 결과가 비어 있지 않고 **첫 항목만** `categoryName=캡스톤`입니다. 전체 항목이나 다른 분류 표시명을 검증하는 것은 아닙니다.
- **코드:** `category_display_name_is_converted`

### IT-20. 카드의 지정 필드에 값이 존재

- **준비:** 시드와 조건 없는 요청.
- **절차:** 1. `emptyRequest()` 생성. 2. 검색. 3. 각 항목의 지정 필드를 검사.
- **기대 결과:** 반환된 모든 항목에서 `projectId`, `projectName`, `creatorName`, `categoryCode`, `categoryName`, `platformName`이 null이 아닙니다. 빈 문자열·내용의 정확성·그 외 필드·결과가 비어 있지 않음은 별도로 단언하지 않습니다.
- **코드:** `required_fields_are_not_null`

### IT-21. 테스트에서 새로 저장한 프로젝트 검색

- **준비:** `DynamicDataTests` 중첩 그룹. 이 그룹에는 같은 SQL을 `BEFORE_TEST_CLASS`에 실행하는 추가 `@Sql` 선언이 있습니다. 상위 클래스의 SQL·트랜잭션 선언도 있으므로 최초 한 번만 삽입하거나 모든 준비가 메서드 롤백으로 사라진다고 가정하지 않습니다.
- **절차:** 1. `memberRepository.save(MemberFixture.withName("신규리더"))`로 회원 저장. 2. 해당 회원을 리더로 `projectRepository.save(ProjectFixture.withName("완전새로운프로젝트XYZ", newLeader))` 저장. 3. 같은 문자열로 키워드 검색 후 내용 검사.
- **기대 결과:** 결과가 정확히 1개이고 첫 항목의 프로젝트명이 `완전새로운프로젝트XYZ`입니다. 회원 필드·생성된 ID 값·HTTP 등록·새 트랜잭션이나 재기동 후 영속성은 검증하지 않습니다.
- **코드:** `searches_newly_created_project`

## 5. 남은 확인과 결과 기록

모집 상태의 실제 값, 정렬된 모든 항목의 순서, 페이지 간 중복·누락, 로그인 사용자의 좋아요 등은 추가 검증이 필요합니다. 이름에 AI가 포함된 결과의 정확한 개수나 모든 카드의 표시명도 전체 검증된 것은 아닙니다.

실행 결과는 [양식](../results/TEMPLATE.md)에 IT 번호별로 기록합니다. DB·Redis 기동 문제는 검색 결과 불일치와 구분합니다. 보고서를 보관한 뒤 [공통 안내](../README.md)의 7절에 따라 정리하며 운영 DB나 공유 Redis는 건드리지 않습니다.

## 6. 관련 코드

- [검색 통합 테스트](../../../src/test/java/com/wardk/meeteam_backend/integration/project/service/ProjectQueryServiceIntegrationTest.java)
- [검색 서비스](../../../src/main/java/com/wardk/meeteam_backend/domain/project/service/ProjectQueryServiceImpl.java) · [검색 요청](../../../src/main/java/com/wardk/meeteam_backend/web/project/dto/request/ProjectSearchRequest.java)
- [MemberFixture](../../../src/test/java/com/wardk/meeteam_backend/fixture/MemberFixture.java) · [ProjectFixture](../../../src/test/java/com/wardk/meeteam_backend/fixture/ProjectFixture.java)
- [테스트 설정](../../../src/test/resources/application-test.yml) · [전체 테스트 컨벤션](../../TEST_CONVENTION.md)
