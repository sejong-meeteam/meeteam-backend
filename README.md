# MeeTeam Backend

세종대학교 구성원을 위한 프로젝트 모집 및 팀원 매칭 서비스의 백엔드입니다.
Spring Boot 기반 REST API와 SSE 실시간 알림을 제공합니다.

## 기술 스택

- **Java 17 / Spring Boot 3.4.5**
- **Spring Data JPA / QueryDSL**: 데이터 접근 및 검색
- **Spring Security / JWT(JJWT)**: 인증·인가 및 토큰 갱신
- **MySQL**: 배포 환경의 관계형 데이터 저장
- **Redis**: 인증 일회용 코드·토큰 블랙리스트 및 SSE 재전송 캐시·알림 Pub/Sub
- **Ehcache / JCache**: 메인 페이지 프로젝트 조회 캐시
- **Swagger/OpenAPI / Lombok**: API 문서화 및 코드 생성
- **Spring Boot Actuator**: 애플리케이션 상태 확인
- **Docker / Docker Compose**: 애플리케이션 및 데이터 서비스 실행
- **GitHub Actions**: 빌드 및 테스트 EC2 자동 배포

## 주요 기능

- 세종대 포털 기반 로그인·가입, JWT 인증, 토큰 갱신 및 로그아웃
- 회원 프로필 조회·수정 및 팀원 검색
- 프로젝트 등록·조회·검색·수정·삭제 및 모집 상태 관리
- 프로젝트 지원, 지원자 조회·승인·거절 및 지원 취소
- 프로젝트 팀원 조회·관리 및 탈퇴
- 프로젝트 좋아요
- 프로젝트 Q&A 조회·질문·답변·삭제
- 알림 목록·미확인 개수 조회 및 SSE 실시간 알림
- 직무·기술 스택 정보 조회

기능 목록은 현재 소스에 구현된 API 기준입니다. 모든 화면과 배포 환경의 전체 시나리오 검증 완료를 의미하지는 않습니다.

## 프로젝트 구조

```
src/main/java/com/wardk/meeteam_backend/
├── MeeteamBackendApplication.java          # 애플리케이션 진입점
├── domain/                                # 도메인 모델·저장소·서비스
│   ├── application/                       # 프로젝트 지원 및 승인·거절
│   ├── file/                              # 파일 관리
│   ├── job/                               # 직무 및 포지션
│   ├── member/                            # 회원 관리
│   ├── notification/                      # 알림 및 SSE
│   ├── project/                           # 프로젝트 관리
│   ├── projectlike/                       # 프로젝트 좋아요
│   ├── projectmember/                     # 프로젝트 팀원 관리
│   ├── qna/                               # 프로젝트 질문·답변
│   ├── recruitment/                       # 모집 관리
│   └── skill/                             # 기술 스택 관리
├── global/                                # 인증·설정·공통 기능
└── web/                                   # API 컨트롤러·요청/응답 DTO
```

## 빌드 및 실행 환경

- 빌드에는 Java 17과 저장소의 Gradle Wrapper를 사용합니다.
- EC2 테스트 배포와 `compose-test.yml`은 MySQL 8.0과 Redis 7을 사용합니다.
- 자동화 테스트의 `src/test/resources/application-test.yml`은 H2 메모리 DB와 Redis `localhost:6379`를 지정합니다. 배포용 `src/main/resources/application-test.yml`과 구분하며, 실행 전 테스트 데이터와 외부 호출의 격리를 확인해야 합니다.
- 컨테이너 실행 및 자동 배포에는 Docker와 Docker Compose가 필요합니다.
- DB·Redis·JWT·AWS 설정 등은 실행 환경에 맞게 별도로 준비해야 합니다. 환경변수 예시는 [.env.test.example](.env.test.example)을 참고합니다.
- Spring 프로파일은 실행 환경에 맞게 지정합니다. 현재 테스트 배포는 `test` 프로파일을 사용합니다.
- 비밀번호·키·토큰과 실제 환경변수 파일은 저장소에 올리지 않습니다.

테스트를 제외하고 배포용 JAR를 빌드하는 명령은 다음과 같습니다.

```bash
bash ./gradlew bootJar -x test
```

이 명령은 JAR만 빌드하며 DB·Redis나 서버를 실행하지 않습니다.
현재 저장소의 Compose 파일에는 ECR 이미지 주소가 포함되어 있으므로, 새 환경에서는 이미지와 환경 설정을 확인한 뒤 사용해야 합니다.
기존 EC2 배포 동작은 [배포 워크플로](.github/workflows/deploy-main.yml)와 [배포 스크립트](scripts/deploy-main.sh)를 참고합니다.

## API 문서 및 상태 확인

| 용도 | 로컬 실행 시 주소 | 테스트 배포 주소 |
| --- | --- | --- |
| API 서버 | http://localhost:8080 | https://meeteam.fignal.me |
| Swagger UI | http://localhost:8080/swagger-ui.html | https://meeteam.fignal.me/swagger-ui.html |
| 상태 확인 | http://localhost:8080/actuator/health | https://meeteam.fignal.me/actuator/health |

로컬 주소는 해당 컴퓨터에서 앱을 실행했을 때 사용하는 주소입니다.
배포 주소는 EC2와 앱이 실행 중이고 네트워크 접근이 허용되어 있어야 사용할 수 있습니다.
Swagger 경로는 코드 설정 기준이며, 표의 주소가 항상 접속 가능하다는 의미는 아닙니다.

## 실시간 알림

인증된 사용자는 `GET /api/v1/subscribe`로 SSE 알림 스트림을 구독합니다.
알림 목록과 미확인 개수는 `GET /api/v1/notifications` 및 `GET /api/v1/notifications/unread/count`로 조회합니다.
브라우저에서 EventSource로 연결할 때는 인증 쿠키 전달과 CORS 설정을 확인해야 합니다. 프론트엔드 프록시의 구현·배포 상태는 이 백엔드 저장소만으로 보장하지 않습니다.

## 파일 저장

S3 업로드·삭제 서비스 코드가 포함되어 있습니다.
실제 사용에는 유효한 버킷·접근 권한·환경변수가 필요하며, 현재 테스트 환경의 S3 연결 성공과 이미지 저장·조회·삭제 검증은 별도로 확인해야 합니다.

## 코드 구성 및 테스트

- `domain`: 엔티티·저장소·서비스
- `web`: 컨트롤러·요청/응답 DTO
- `global`: 인증·설정·예외 처리·공통 응답 등
- `CustomException`, `ErrorCode`, `GlobalExceptionHandler`를 이용한 공통 예외 처리가 구현되어 있습니다.
- 단위·통합·인수 테스트 코드가 포함되어 있습니다. 빌드 성공이 전체 테스트 통과나 특정 커버리지 달성을 의미하지는 않습니다.

## main 테스트 서버 자동 배포

`main` push → 최소 CI(테스트/린트 없음) → JAR 빌드 → 기존 EC2 앱 교체 → 상태 및 테스트 계정 인증 확인.
앱 교체 후 검사에 실패하면 이전 앱 복구를 시도하며 DB·Redis·Nginx·HTTPS·비밀 설정을 유지합니다. 복구 실패 시에는 관리자 조치가 필요합니다.
현재 배포 스크립트는 기존 `.env.test-account` 파일과 `TEST_ACCOUNT_ENABLED=true`를 필수로 확인하며, 학번 `99990001`의 로그인과 쿠키를 이용한 프로필 조회가 실패하면 배포를 실패로 처리합니다.

테스트 배포는 EC2, Elastic IP, DNS 도메인 연결, Nginx 리버스 프록시 및 Certbot 기반 HTTPS 구성을 사용합니다.
자체 호스팅 runner가 설치되어 있고 EC2가 실행 중이어야 자동 배포가 진행됩니다.
runner 최초 설정은 [설치 스크립트](scripts/setup-test-runner.sh)를 참고합니다.
