# 관리자 패키지 구조

Java 루트: `src/main/java/com/wardk/meeteam_backend/`

## 웹 계층

| 패키지 | 클래스와 역할 |
|---|---|
| `web/admin/controller` | `AdminQueryController`, `AdminMutationController`, `AdminAuditController`: 조회·관리 조치·감사 로그 API |
| `web/admin/dto/request` | `AdminMutationRequests`: 관리 요청 파싱 및 검증 |
| `web/admin/dto/response` | `AdminResponses`: 관리자 응답 DTO |
| `web/admin/exception` | `AdminRequestExceptionHandler`: 관리자 컨트롤러 전용 예외 처리 |

## 도메인 계층

| 패키지 | 클래스와 역할 |
|---|---|
| `domain/admin/service` | `AdminQueryService`, `AdminDashboardService`, `AdminMutationService`, `AdminMemberActionService`, `AdminProjectActionService`, `AdminAuditQueryService` |
| `domain/admin/repository` | `AdminQueryRepository`, `AdminDashboardRepository`, `AdminAuditWriter`: 조회 및 감사 로그 저장 |
| `domain/admin/entity` | `AdminAuditLog`, `AuditAction`, `AuditTargetType`: 감사 로그 엔티티와 분류 |
| `domain/admin/policy` | `AdminActionGuard`, `AdminReason`: 권한·잠금·사유 검증 |
| `domain/admin/mapper` | `AdminResponseMapper`: 응답 변환 |
| `domain/admin/dto` | `AdminQueryOptions`: 검색·정렬·페이지 조건 |
| `domain/admin/config` | `AdminTimeConfig`: 관리자 기능의 시간 설정 |

관리자 인증과 비밀번호 설정 도구는 기존 `global/auth/service`에 유지합니다.

이 정리는 패키지와 참조 경로만 변경합니다. `/api/v1/admin/**`의 URL, 요청·응답 구조, 권한 규칙과 DB 테이블 이름은 변경하지 않습니다. 마이그레이션 재실행이나 관리자 비밀번호 재설정도 필요하지 않습니다.

`GithubLifecycleAspect`의 관리자 조치 메서드 경로도 새 `domain.admin.service` 패키지로 맞춥니다. 이는 기존 경로 참조 보존이며 GitHub 연동을 활성화하는 변경이 아닙니다.

패키지 배치·옛 클래스 중복 방지·AOP 경로·감사 엔티티 이름의 회귀 검사는 `src/test/java/com/wardk/meeteam_backend/adminlayoutunit/AdminPackageStructureTest.java`에 있습니다.

## 로컬 검증 기록

- 깨끗한 HEAD 사본에 이번 패키지 변경과 참조 수정만 반영한 격리 환경에서 선별 회귀 테스트 563개가 통과했습니다. 실패·오류·건너뜀은 0이며, 새 패키지 구조 검사 4개가 포함됩니다.
- 테스트 실행 사본에만 기존 Cucumber/JUnit 호환을 위한 JUnit 버전을 지정했습니다. 작업 저장소의 기존 `build.gradle`과 미완료 테스트 변경은 그대로 보존했습니다.
- 테스트 이후 사본의 빌드 설정을 게시된 원본으로 되돌려 Java 17 `bootJar -x test`를 성공시켰습니다. JAR에서 새 controller/service/entity 경로와 재생성된 `QAdminAuditLog`를 확인했습니다.
- 기존 코드 33개 파일은 패키지·import·해당 클래스의 완전한 이름 참조를 제외한 본문이 동일함을 대조했습니다. 기존 미완료 작업 17개 파일도 변경 전후 바이트가 일치했습니다.
- 이는 로컬 패키지 정리 검증이며, 커밋·푸시·실서버 재배포를 수행했다는 뜻은 아닙니다.
