# 프로젝트 수정 정책

## 기준과 적용 범위

공개 커밋 `3865b451663788792d7e78c736282f8b9ff105f1`의 소스를 `git show <commit>:<path>`로 확인한 정책입니다. 실행·테스트·배포 동작을 검증한 결과가 아닙니다. 프론트엔드 소스를 확인한 문서도 아니므로 모달·버튼·폼 동작은 **UI 권장안**으로 구분합니다.

실제 수정 경로는 `ProjectManagementController` → `ProjectEditServiceImpl`입니다. `ProjectCommandService`/구현체는 생성·삭제를 담당하며, 생성 시 사용하는 검증을 수정에서도 한다고 가정하면 안 됩니다. 요청·응답 전체 템플릿은 [프로젝트 수정 API 가이드](project-edit-api-guide.md)를 참고하세요.

## 1. 조회·수정 권한과 상태

- `GET /api/v1/projects/{projectId}/edit`, `PUT /api/v1/projects/{projectId}` 모두 인증 및 리더 권한이 필요합니다.
- 리더 여부는 프로젝트 **creator의 이메일**과 요청자 이메일 비교로 검증합니다. 일반 팀원은 수정할 수 없습니다.
- 리더가 아니면 `PROJECT_MEMBER403` / HTTP 403 / `해당 프로젝트 관리 권한이 없습니다.`입니다. 선언된 `PROJECT_EDIT_FORBIDDEN`은 이 경로에서 사용하지 않습니다.

| 프로젝트 상태 | Pre-fill | PUT 수정 | 수정 후 동작 |
|---|---|---|---|
| `RECRUITING` 모집중 | 리더 조회 가능, editable=true | 가능 | 인원에 따라 상태 재계산 |
| `SUSPENDED` 수동 모집중단 | 리더 조회 가능, editable=false와 사유 반환 | 불가, HTTP 400 / PROJECT400 | 모집 재개 후 수정 필요 |
| `CLOSED` 모집마감 | 리더 조회 가능, editable=true | 가능 | 정원을 늘리거나 신규 모집 추가 시 재개 가능 |

수동 재개는 `POST /api/v1/projects/{projectId}/recruitment/toggle`입니다. SUSPENDED에서 재개하면 미달 포지션만 열고, 모든 정원이 차 있으면 프로젝트는 다시 CLOSED가 됩니다.

**UI 권장안:** Pre-fill을 폼에 채우고 editable=false이면 수정 제출을 막으며 사유를 표시합니다. 조회와 저장 사이 상태가 바뀔 수 있으므로 PUT 오류도 처리해야 합니다.

## 2. 수정 항목과 실제 적용 범위

| 항목 | 구현된 계약 / 제약 |
|---|---|
| 프로젝트명 | 수정 필드는 `name`(생성의 `projectName`과 다름), 필수·공백 불가 |
| 소개글 | 선택, 최대 5,000자. null이면 null 대입 |
| 카테고리 | 필수. CAPSTONE / CREATIVE_SEMESTER / CLUB / ETC. IT_SERVICE는 유효하지 않음 |
| 플랫폼 | 필수. IOS / ANDROID / WEB |
| GitHub·소통 링크 | 선택(null 허용), 지정된 URL 패턴 검증. 생략/null은 기존 값 유지가 아니라 null 대입 |
| 이미지 | 새 파일이 있으면 업로드 후 URL 교체. 생략/빈 파일이면 유지. 이미지 삭제용 요청 필드 없음 |
| 마감 방식 | recruitmentDeadlineType 필수, END_DATE 또는 RECRUITMENT_COMPLETED |
| 마감일 | endDate는 DTO에서 선택이며 그대로 저장. 수정 경로에는 날짜/방식 조합·과거 날짜 검증이 없음 |
| 리더 포지션 | leaderJobPositionCode는 필수지만 Command 전달 후 실제 갱신에 사용하지 않음. **변경 지원으로 표시하지 않음** |
| 기존 모집 행 직군·포지션 | 코드는 필수지만 기존 행의 정원·스택만 수정. 직군·포지션 변경 미적용 |
| 모집 추가 | recruitmentStateId=null인 행을 jobPositionCode로 조회하여 추가 |
| 모집 삭제 | 전체 요청 목록에서 빠진 기존 행을 삭제 대상으로 처리. 아래 조건 적용 |
| 모집 정원 | 최소 1 및 해당 행의 currentCount 이상 |
| 기술 스택 | 요청 ID 배열 최소 1개. 조회된 스택으로 교체. 존재·직군 매핑·중복까지 검증된다고 보장하지 않음 |

Pre-fill에는 `recruitmentDeadlineType`과 `leaderJobPositionCode`가 없고 리더의 **표시명**만 있습니다. 폼 초기화 시 이 누락을 고려해야 하며, 없는 코드 필드를 반환한다고 문서화하면 안 됩니다. 마감 방식은 별도 프로젝트 상세 응답에서 확인할 수 있습니다.

**권장 정책과 현재 구현의 차이:** 생성 서비스는 `RecruitmentDeadline` 값 객체로 END_DATE의 필수 날짜·과거 날짜 및 RECRUITMENT_COMPLETED의 null 날짜를 검증합니다. 수정 서비스는 이 객체를 만들지 않고 바로 대입합니다. UI에서는 마감 방식에 맞는 날짜 조합을 보내는 것이 좋지만, 이를 수정 API의 검증 보장으로 표현하지 않습니다.

## 3. 모집 목록은 전체 교체 의도 목록

| 요청 행의 ID | 동작 |
|---|---|
| 현재 프로젝트에 존재하는 recruitmentStateId | 기존 행의 정원·스택 갱신 |
| null(필드 생략 포함) | 신규 행 생성 |
| 기존 행 전체가 요청 배열에 없음 | 해당 기존 행 삭제 시도 |
| 현재 프로젝트에 없는 비-null ID | 기존 갱신 분기에서 무시. 신규 생성/명시적 404 보장 없음 |

요청 `recruitments`는 최소 1개가 필요하므로 전부 삭제하는 빈 배열은 유효하지 않습니다. 일부 변경 행만 보내면 다른 기존 행들이 삭제 대상이 됩니다. 기존 행에서 ID만 빼면 기존 행 삭제+신규 생성 의도가 되어 승인 인원/대기 지원자 제약에 걸릴 수 있습니다.

기존 행의 직군·포지션을 바꾸려면 삭제 조건을 충족한 뒤 삭제+신규 추가로 처리해야 합니다. 중복 ID·중복 포지션·다른 프로젝트 ID를 안전하게 교정하거나 검증해 준다고 가정하지 마세요. 화면은 조회된 기존 ID를 보존하고 신규 행에만 null을 사용하는 것이 권장됩니다.

## 4. 정원과 삭제 제한

### 정원 축소

**currentCount 미만**으로만 줄일 수 없습니다. **같은 수는 허용**합니다. 단, DTO 최소 1 조건도 동시에 적용합니다.

| 현재 승인/정원 | 요청 정원 | 결과 |
|---|---|---|
| 2/4 | 3 | 허용, 2/3 모집중 |
| 2/4 | 2 | 허용, 2/2 마감 |
| 2/4 | 1 | 거절, RECRUITMENT400 |
| 0/2 | 0 | DTO 최소 1 검증 실패, COMMON400 |

Pre-fill `minRecruitmentCount`는 currentCount 그대로여서 0일 수 있습니다. UI 입력 최솟값은 `Math.max(1, minRecruitmentCount)`를 권장합니다. 서버는 저장 시점의 currentCount로 다시 판단합니다.

### 승인 인원이 있는 행 삭제

currentCount > 0이면 삭제 불가입니다. `deletable`도 currentCount===0일 때만 true입니다. 대기 지원자 확인 플래그를 true로 보내도 승인 인원 제약을 우회하지 못합니다.

### 대기 지원자가 있는 행 삭제

승인 인원이 0인 삭제 대상에 프로젝트·포지션 기준 PENDING 지원서가 있다면:

1. `confirmDeletePositionsWithPendingApplicants`가 생략/null/false이면 HTTP **409**, `RECRUITMENT409`로 거절합니다.
2. true이면 해당 PENDING 지원서들을 REJECTED로 변경하고 모집 행을 제거합니다.
3. 성공 응답의 `autoRejectedApplicantCount`에 변경한 지원서 수를 합산합니다. 다른 상태의 지원서까지 모두 거절한다는 의미가 아닙니다.

플래그는 요청 전체에 하나이므로 삭제 대상의 대기 지원서 처리에 공통 적용됩니다. 이 수정 경로에는 자동 거절 알림 발송 호출이 없습니다. 알림 전송을 보장하지 않습니다. 서비스는 트랜잭션 경계 안에서 처리하지만 외부 이미지 업로드까지 DB 롤백된다고 보장하지 않습니다.

**UI 권장안(구현 확인 아님):** 삭제 대상의 pendingApplicationCount를 안내하고 “해당 포지션의 대기 지원이 자동 거절됩니다. 삭제할까요?” 같은 확인을 받습니다. 취소하면 행을 유지하고, 확인한 경우에만 true로 보냅니다. 409 응답을 이유로 사용자 동의 없이 플래그를 자동 전환하지 않습니다.

## 5. 모집 상태 재계산

기존 행 정원을 변경한 뒤 currentCount >= recruitmentCount이면 닫고, 미달이면 다시 엽니다. 신규 모집은 정원 미달 상태로 추가됩니다.

전체 수정 후 `updateRecruitmentStatusBasedOnPositions()`는 모든 모집 행의 **인원**을 검사합니다. 모두 정원 이상이면 CLOSED, 하나라도 미달이면 RECRUITING입니다. 이 재계산은 recruitmentDeadlineType이나 endDate를 검사하지 않습니다. 수동 중단 상태는 자동 변경하지 않지만 PUT에서는 그 전에 수정 불가 검증을 합니다.

| 수정 전 | 수정 | 수정 후 |
|---|---|---|
| 마감 3/3 | 정원 4 | 해당 행 모집중 3/4, 프로젝트 RECRUITING |
| 모집중 2/3 | 정원 4 | 모집중 2/4 |
| 모집중 2/4 | 정원 2 | 해당 행 마감. 다른 행까지 모두 차면 프로젝트 CLOSED |

날짜 기반 마감의 다른 처리 경로까지 이 표가 설명하는 것은 아닙니다. 이 문서는 수정 서비스가 수행하는 재계산에 한정합니다.

## 6. 오류 식별

정상 응답은 `{code, message, result}`, 처리된 오류는 `{code, message}`이며 `isSuccess`나 `data`가 없습니다. enum 이름을 그대로 응답 코드로 사용하지 않습니다.

| 상황 | HTTP / 응답 code | 실제 메시지 |
|---|---|---|
| 권한 없음 | 403 / PROJECT_MEMBER403 | 해당 프로젝트 관리 권한이 없습니다. |
| 모집중단에서 수정 | 400 / PROJECT400 | 모집이 중단된 상태에서는 수정할 수 없습니다. 모집을 재개한 후 수정해주세요. |
| 정원 < 승인 인원 | 400 / RECRUITMENT400 | 현재 승인된 인원보다 적게 설정할 수 없습니다. |
| 승인 인원이 있는 행 삭제 | 400 / RECRUITMENT400 | 승인된 팀원이 있는 포지션은 삭제할 수 없습니다. |
| 대기 지원자 삭제 미확인 | 409 / RECRUITMENT409 | 대기 중인 지원자가 있는 포지션입니다. 삭제 시 해당 지원자들의 지원이 자동 거절됩니다. |

정원 오류 메시지에 동적인 `{n}명`은 들어가지 않습니다. `RECRUITMENT400` 등은 여러 원인에서 공유하므로 code만으로 개별 상황을 식별할 수 없습니다. 예상치 못한 예외는 전역 핸들러에서 500/문자열로 반환할 수 있으므로 모든 실패가 위 JSON 형태라고 가정하지 않습니다.

## 7. 소스 근거 (기준 커밋의 줄 번호)

모든 경로의 접두사는 `src/main/java/com/wardk/meeteam_backend/`입니다.

| 항목 | 경로:줄 |
|---|---|
| GET/PUT 수정 라우트와 실제 서비스 위임 | `web/project/controller/ProjectManagementController.java:54-78` |
| 토글 라우트 | `web/project/controller/ProjectManagementController.java:34-41` |
| 생성/삭제만 담당하는 CommandService | `domain/project/service/ProjectCommandService.java:11-31` |
| Pre-fill 권한·PENDING 집계 | `domain/project/service/ProjectEditServiceImpl.java:60-89` |
| 수정 권한·기본 정보·상태 재계산·리더 코드 미사용 | `domain/project/service/ProjectEditServiceImpl.java:94-130` |
| 삭제 제약·거절·ID 분기·스택·정원 | `domain/project/service/ProjectEditServiceImpl.java:133-226` |
| creator 이메일 권한·SUSPENDED 제한 | `domain/project/entity/Project.java:426-447,468-471` |
| 모집 재개·전체 인원 기준 재계산 | `domain/project/entity/Project.java:242-289` |
| 기본 정보·이미지 대입 | `domain/project/entity/Project.java:344-365` |
| 정원/기술 스택/모집 목록 요청 검증 | `web/project/dto/request/RecruitmentEditRequest.java:16-35`, `web/project/dto/request/ProjectEditRequest.java:26-68` |
| Pre-fill 전체 필드·리더 표시명 | `web/project/dto/response/ProjectEditPrefillResponse.java:18-62,79-95` |
| 삭제 가능 여부·최솟값 | `web/project/dto/response/RecruitmentEditInfo.java:60-89` |
| 생성의 날짜 정책(수정과 구분) | `domain/project/service/ProjectCommandServiceImpl.java:55-66`, `domain/project/vo/RecruitmentDeadline.java:21-44` |
| 오류 상태·코드·메시지 | `global/response/ErrorCode.java:56-60,72`, `global/exception/handler/GlobalExceptionHandler.java:24-32,99-104` |
| 성공 응답 필드 | `web/project/dto/response/ProjectEditResponse.java:13-35`, `global/response/SuccessResponse.java:10-25`, `global/response/SuccessCode.java:10` |
