# 5W1H 프레임워크 기반 인수조건 정의

프로젝트 생성 및 권한 관리 시나리오에 대한 테스트 목적과 범위를 정의합니다.

**상태:** 이 문서는 인수조건·보완 계획입니다. 게시 GitHub `main` 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`을 `git show`로 정적 검토했으며 테스트 실행·통과를 확인하지 않았습니다. 미체크 항목은 요구사항 추적 대상이며 구현 부재를 일괄 의미하지 않습니다. 로컬 미커밋 acceptance/config 수정은 제외합니다.

## 시나리오: 프로젝트 생성 및 리더 권한 자동 부여

| 구분 | 내용 | 비고 |
| :--- | :--- | :--- |
| **Who (누가)** | 서비스에 가입하고 로그인한 **일반 회원**이 | 인증된 사용자 권한 필요 |
| **When (언제)** | 팀원을 모집하기 위해 **새로운 프로젝트를 등록**할 때 | 프로젝트 생성 API 호출 시 |
| **Where (어디서)** | **ProjectController → ProjectCommandServiceImpl → ProjectMemberService.addCreator()**를 통해 | 도메인 로직 수행 영역 |
| **What (무엇을)** | 프로젝트 엔티티를 생성함과 동시에 본인을 **리더(LEADER) 역할**로 | ProjectMemberRole.LEADER |
| **Why (왜)** | 프로젝트에 대한 **관리 권한(수정, 삭제, 팀원 승인 등)을 보장**하기 위해 | 권한 계층 구조의 기초 |
| **How (어떻게)** | `ProjectMember` 테이블에 사용자 ID와 함께 **LEADER 역할을 저장**하여 검증 | DB 정합성 및 API 응답 확인 |

---

## 상세 인수조건 (Acceptance Criteria, 실행 완료 체크리스트 아님)

### 1. 기능적 요구사항
- [ ] 회원이 프로젝트 생성 시, `project_member` 테이블에 해당 회원의 정보가 추가되어야 한다.
- [ ] 이때 추가되는 회원의 `role` 값은 반드시 `LEADER`여야 한다.
- [ ] 리더로 지정된 사용자는 이후 프로젝트 상세 조회 시 리더 여부(`isLeader`)가 `true`로 반환되어야 한다.

### 2. 비기능적 요구사항 (예외 케이스)
- [ ] 로그인하지 않은 사용자가 프로젝트 생성을 시도할 경우, 권한 에러(401 Unauthorized)를 반환해야 한다.
- [ ] 프로젝트 생성 과정에서 리더 권한 부여 로직이 실패할 경우, 전체 프로세스는 롤백(Rollback)되어야 한다.

---

## 테스트 설계 전략 (ATDD Connection)

- **Given:** 가입된 회원이 로그인하여 유효한 인증 쿠키를 보유한 상태에서 (게시 헬퍼의 Bearer 구조는 현재 인증 흐름과 별도 대조 필요)
- **When:** `POST /api/v1/projects`에 multipart `request` JSON과 선택적 `file`로 필수 정보를 제출하면
- **Then:** HTTP 200 및 `code=COMMON200` 응답, 리포지토리의 생성 회원 ID·프로젝트 ID·`LEADER` 역할을 확인한다. 현재 기존 Step은 역할과 회원 이름을 검사하므로 ID 기준 보강은 향후 과제이다.


## 게시 구현·기존 테스트·남은 검증

아래 경로·줄은 기준 커밋의 소스입니다. `src/main/java/com/wardk/meeteam_backend/` 및 `src/test/java/com/wardk/meeteam_backend/`를 각각 main·test 경로의 기준으로 합니다.

| 인수조건 | 게시 구현/기존 테스트 근거 | 남은 공백 |
| --- | --- | --- |
| 프로젝트 생성과 생성자 연결 | main `web/project/controller/ProjectController.java:33-44`, `domain/project/service/ProjectCommandServiceImpl.java:65-79,136-141` | 실제 API 실행·저장 결과 미확인 |
| 생성자 LEADER 역할 | main `domain/projectmember/service/ProjectMemberServiceImpl.java:33-46`; test `acceptance/cucumber/steps/ProjectCreateSteps.java:166-179`; `src/test/resources/features/project-create.feature:11-18` | 기존 구현·Then은 존재. 신규 테스트 작성 또는 통과가 아님 |
| 상세 `isLeader=true` | main `domain/project/service/ProjectQueryServiceImpl.java:64-75`, `web/project/dto/response/ProjectDetailResponse.java:77-78,110-139` | DTO 필드 존재와 검증은 다름. 기존 생성 Step의 상세 검사는 `ProjectCreateSteps.java:182-192`에서 마감 방식만 검사 |
| 비인증 401 | main `global/config/SecurityConfig.java:57-66`, `global/exception/RestAuthenticationEntryPoint.java:26-31`; `src/test/resources/features/project-create.feature:89-94` | 기존 생성 실패 Step은 `ProjectCreateSteps.java:161-164`에서 400 이상만 확인하므로 401을 특정하지 않고 500도 허용. 정확한 상태·오류 코드 검증 필요 |
| 리더 지정 실패 시 롤백 | main `domain/project/service/ProjectCommandServiceImpl.java:40,65-75`의 트랜잭션/호출 구조 | 실패 주입과 DB 잔존 여부를 검증하는 독립 테스트는 확인하지 못함. 트랜잭션 annotation만으로 실행 검증을 대체하지 않음 |

공통 선행 과제는 레거시 인증 헬퍼의 세종대 API 계약 반영, 전체 Step 연결, 엔진·suite 실행 설정, DB·Redis·외부 서비스 격리입니다. [테스트 컨벤션](../../TEST_CONVENTION.md)을 기준으로 구현·계획·실행 결과를 분리해 기록합니다.
