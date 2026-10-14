# MeeTeam Backend

개발자들을 위한 팀 매칭 및 프로젝트 협업 플랫폼의 백엔드 서버입니다.

## 📋 프로젝트 개요

MeeTeam은 개발자들이 팀을 구성하고 프로젝트를 협업할 수 있도록 도와주는 플랫폼입니다. 이 저장소는 Spring Boot 기반의 백엔드 API 서버를 제공합니다.

## 🛠 기술 스택

### Core Framework
- **Java 17**
- **Spring Boot 3.4.5**
- **Spring Data JPA**
- **Spring Security**
- **Spring WebSocket** - 실시간 채팅 기능
- **Spring Messaging** - STOMP 프로토콜 지원

### Database & Cache
- **MySQL** - 메인 데이터베이스
- **Redis** - 캐시 및 세션 저장소
- **QueryDSL** - 타입 세이프 쿼리

### Authentication & Authorization
- **Spring OAuth2 Client** (Google, GitHub 로그인)
- **JWT (JSON Web Token)**
- **Auth0 Java JWT**

### AI & External Services
- **Spring AI with OpenAI** - GPT-4o-mini 모델 활용
- **AWS S3** - 파일 저장소

### Monitoring & DevOps
- **Prometheus** - 메트릭 수집
- **Grafana** - 모니터링 대시보드
- **Spring Actuator** - 애플리케이션 헬스체크
- **Docker & Docker Compose**

### Development Tools
- **Swagger/OpenAPI 3** - API 문서화
- **P6Spy** - SQL 로깅
- **Lombok** - 코드 생성 자동화

## 🏗 프로젝트 구조

```
src/main/java/com/wardk/meeteam_backend/
├── MeeteamBackendApplication.java          # 메인 애플리케이션 클래스
├── domain/                                 # 도메인 로직
│   ├── applicant/                          # 지원자 관리
│   ├── category/                           # 카테고리 관리
│   ├── chat/                              # 채팅 기능
│   ├── codereview/                        # 코드 리뷰
│   ├── file/                              # 파일 관리
│   ├── llm/                               # AI/LLM 연동
│   ├── member/                            # 회원 관리
│   ├── notification/                      # 알림 시스템
│   ├── pr/                                # Pull Request 관리
│   ├── project/                           # 프로젝트 관리
│   ├── projectLike/                       # 프로젝트 좋아요
│   ├── projectMember/                     # 프로젝트 멤버 관리
│   ├── review/                            # 리뷰 시스템
│   ├── skill/                             # 기술 스택 관리
│   └── webhook/                           # 웹훅 처리
├── global/                                # 전역 설정 및 공통 기능
└── web/                                   # 웹 계층 (컨트롤러)
```

## 🚀 시작하기

### 사전 요구사항
- Java 17 이상
- MySQL 8.0 이상
- Redis 7.0 이상
- Docker & Docker Compose (선택사항)



이 명령어로 다음 서비스들이 실행됩니다:
- **MeeTeam Backend Server** (포트 8080)
- **Redis** (포트 6379)
- **Prometheus** (포트 9090)
- **Grafana** (포트 3000)

## 📊 모니터링

### Prometheus
- URL: http://localhost:9090
- 애플리케이션 메트릭 수집

### Grafana
- URL: http://localhost:3000
- 기본 계정: admin/admin
- 시각화 대시보드 제공

### Spring Actuator
- Health Check: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics

## 📚 API 문서

Swagger UI를 통해 API 문서를 확인할 수 있습니다.
- URL: http://localhost:8080/swagger-ui.html

## 🔐 인증 방식

### OAuth2 Social Login
- **Google OAuth2**
- **GitHub OAuth2**

### JWT Token
- Access Token 기반 인증
- 토큰 갱신 메커니즘 제공

## 🤖 AI 기능

OpenAI의 GPT-4o-mini 모델을 활용한 AI 기능을 제공합니다:
- 코드 리뷰 자동화
- 프로젝트 추천
- 개발 관련 질의응답

## 📁 파일 저장

AWS S3를 활용한 파일 업로드 및 관리 기능을 제공합니다.

## 🔄 주요 기능

- **팀 매칭**: 개발자들의 기술 스택과 관심사를 기반으로 한 팀 매칭
- **프로젝트 관리**: 프로젝트 생성, 관리, 진행 상황 추적
- **실시간 채팅**: WebSocket 기반 실시간 커뮤니케이션
  - 프로젝트 팀 채팅방 (프로젝트 생성 시 자동 생성)
  - 1:1 개인 채팅 (프로젝트 지원 전 질문 등)
  - 주제별 채팅방 생성
  - 실시간 메시지 전송/수정/삭제
  - 타이핑 상태 표시
  - 멘션(@username) 기능
  - 읽음/안읽음 상태 관리
  - 실시간 알림 시스템
- **코드 리뷰**: AI 기반 자동 코드 리뷰 및 피드백
- **알림 시스템**: 실시간 알림 및 푸시 알림
- **GitHub 연동**: Pull Request 및 웹훅 연동

## 💬 실시간 채팅 시스템

### WebSocket 연결
```javascript
// 클라이언트 연결 예시
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

// 채팅방 구독
stompClient.subscribe('/topic/chat/{chatRoomId}', (message) => {
    // 메시지 처리
});

// 개인 멘션 알림 구독
stompClient.subscribe('/user/queue/mention', (mention) => {
    // 멘션 알림 처리
});
```

### 채팅방 타입
- **PROJECT**: 프로젝트 기본 팀 채팅방
- **TOPIC**: 주제별 채팅방
- **PRIVATE**: 1:1 개인 채팅방
- **PR_REVIEW**: Pull Request 리뷰 채팅방

### 메시지 기능
- 실시간 메시지 전송/수신
- 메시지 수정/삭제 (작성자 본인만)
- 멘션 기능 (@username)
- 타이핑 상태 실시간 표시
- 읽음/안읽음 상태 추적
- 이미지/파일 첨부 지원 예정

## 👨‍💻 개발 규칙

### Code Documentation
- **Javadoc 적극 활용**: 모든 public 클래스, 메서드, 필드에 대해 Javadoc 작성 필수
  ```java
  /**
   * 사용자 정보를 조회합니다.
   * 
   * @param userId 조회할 사용자 ID
   * @return 사용자 정보 DTO
   * @throws UserNotFoundException 사용자를 찾을 수 없을 때
   */
  public UserDto getUser(Long userId) {
      // implementation
  }
  ```
- **클래스 레벨 문서화**: 각 클래스의 역할과 책임을 명확히 기술
- **API 문서화**: Controller 메서드에는 @Operation 어노테이션과 함께 상세한 설명 작성
- **복잡한 비즈니스 로직**: 인라인 주석으로 로직 설명 추가

### Naming Conventions
- **클래스명**: PascalCase (예: UserService, ProjectController)
- **메서드명**: camelCase (예: getUserInfo, createProject)
- **상수**: UPPER_SNAKE_CASE (예: MAX_FILE_SIZE, DEFAULT_PAGE_SIZE)
- **패키지명**: 소문자, 점으로 구분 (예: com.wardk.meeteam_backend.domain.member)

### Code Structure
- **Domain-Driven Design** 원칙 적용
- **계층형 아키텍처**: Controller → Service → Repository
- **단일 책임 원칙** 준수
- **의존성 주입**을 통한 느슨한 결합

### Error Handling
- **CustomException** 및 **ErrorCode** enum 활용
- **GlobalExceptionHandler**를 통한 중앙 집중식 예외 처리
- 사용자 친화적인 에러 메시지 제공

### Testing
- **단위 테스트** 작성 권장
- **통합 테스트**로 API 동작 검증
- **테스트 커버리지** 80% 이상 유지 목표

## 📞 연락처

프로젝트와 관련된 문의사항이 있으시면 이슈를 생성해 주세요.
