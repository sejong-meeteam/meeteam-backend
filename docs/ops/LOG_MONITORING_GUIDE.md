# 서버 로그 및 모니터링 가이드

> 기준: 게시 코드 `3865b451663788792d7e78c736282f8b9ff105f1` 정적 대조. 아래 명령은 승인된 운영자가 필요할 때 사용하는 예시이며 이번 감사에서는 실행하지 않았습니다. 실제 배포, 서비스 상태, 수집 성공, 대시보드 작동은 미검증입니다.

## 1. 코드로 확인되는 구성

- `compose.yml:2-48`: 애플리케이션 서비스/컨테이너는 `meeteam-blue`, `meeteam-green`입니다. 호스트 포트는 각각 8080, 8081이며 컨테이너 내부 8080입니다. `meeteam-backend`라는 이름을 전제로 하지 않습니다. 두 서비스의 선언은 둘 다 현재 트래픽을 받는다는 증거가 아닙니다.
- `compose.yml:18-23,42-47`: healthcheck는 컨테이너 내부 `/actuator/health`에 wget으로 요청합니다. 선언이 있다는 사실과 healthcheck 통과는 다릅니다.
- `compose.yml:66-91`: Prometheus/Grafana 서비스가 선언되어 있고 포트는 각각 9090/3000입니다. Prometheus는 `./prometheus/prometheus.yml`을 마운트하지만 이 커밋 트리에는 해당 파일이 없습니다. scrape target·수집 성공·Grafana datasource/dashboard provisioning은 저장소로 확인하지 못했습니다. Compose 실행만으로 모니터링 완료라고 판단하지 않습니다.
- `build.gradle:45`: Actuator 의존성이 있습니다. 이 커밋의 build.gradle에는 Prometheus registry 의존성(`micrometer-registry-prometheus`)이 없으므로 `/actuator/prometheus` 노출이나 Prometheus 수집 가능 여부를 단정하지 않습니다.
- `src/main/resources/application-prod.yml:87-95`: Actuator 웹 노출은 `include: "*"`, health는 `show-components: always`입니다. `global/config/PublicEndpoint.java:22-23`은 `/actuator/**`를 인증 예외로 둡니다. **내부 전용이라고 가정하면 안 됩니다.** 외부 접근 가능성은 프록시·방화벽·실제 배포 설정을 별도 확인해야 합니다. 공개 URL에 민감한 진단 요청을 보내거나 설정을 덤프하지 않습니다.
- 이 커밋의 Compose에는 별도 logging driver/회전 옵션이 없습니다. Loki/Promtail 서비스나 별도 Logback 설정 파일도 확인되지 않습니다. 호스트 Docker 기본 드라이버·보존 기간·외부 수집·운영 로그 레벨은 미확인입니다.

Java 경로는 `src/main/java/com/wardk/meeteam_backend/` 기준입니다. private `.env` 또는 실제 운영 로그를 읽어 확인한 내용은 없습니다.

## 2. 접속 및 대상 확인 (운영자용 예시)

접근 권한과 대상 서버를 확인하고 SSH 또는 권한이 준비된 Session Manager를 사용합니다. 키 경로·호스트·인스턴스 ID는 실제 승인된 값으로 대체하며 키/환경변수를 문서에 붙여 넣지 않습니다.

```bash
ssh -i <key-path> <user>@<host>
# 또는 IAM/SSM 구성이 준비된 경우
aws ssm start-session --target <instance-id>

# 배포 디렉터리에서 서비스 상태 확인
# 비밀이 확장될 수 있는 docker compose config / env dump는 사용하지 않음
docker compose ps
```

현재 활성 blue/green은 배포 라우팅 정보로 별도 확인합니다. 아래는 blue 예시이며 승인된 조사 대상이 green이면 서비스 이름을 바꿉니다. 현대 Compose CLI는 `docker compose`를 사용하며 구형 환경은 설치 버전을 확인합니다.

## 3. 범위를 제한한 로그 확인

원본 로그에는 토큰·계정·개인정보가 포함될 수 있습니다. 전체 로그 덤프, 실사용자 학번/이메일 기반 검색, 무기한 스트리밍은 기본 절차가 아닙니다. 필요한 최소 시간/줄 수로 제한하고 외부 공유 전에 마스킹합니다.

```bash
# 최근 10분, 최대 100줄
docker compose logs --since 10m --tail 100 meeteam-blue

# 컨테이너 이름으로 확인할 때
docker logs --since 10m --tail 100 meeteam-blue

# 최근 오류만 제한 검색 (grep 결과가 없다고 오류가 없다는 뜻은 아님)
docker compose logs --since 10m --tail 100 meeteam-blue 2>&1 | grep -Ei 'ERROR|exception'

# 필요한 경우에만 짧게 follow하고 종료
docker compose logs --since 1m --tail 30 -f meeteam-blue

# CPU/메모리 참고: 로그 수집 성공이나 장애 원인 증거는 아님
docker stats meeteam-blue --no-stream
```

로그 파일 저장이 꼭 필요하면 접근 제한된 승인 위치에 최소 범위만 저장하고, 보존/삭제 정책과 마스킹을 적용합니다. 원본 로그 파일을 티켓·채팅·공개 저장소에 첨부하지 않습니다.

## 4. 실제 로그 포맷과 해석

`global/logging/MethodLoggingAspect.java:57-76,132-195`는 컨트롤러와 일부 서비스의 시작·완료·예외 및 `실행시간`을 기록합니다. project 패키지 서비스는 해당 서비스 advice에서 제외됩니다. 파라미터/결과는 요약하지만 (`147-150,178-185,219-240`) 이는 비밀/개인정보 마스킹을 보장하지 않습니다. 이 실행시간은 메서드 실행시간이며 전체 네트워크 지연이나 SSE 연결 수명의 측정값이 아닙니다. URI/키워드가 없는 로그도 있으므로 단순 grep은 완전한 요청 추적이 아닙니다.

SSE 관련 구현 로그 근거:

- `domain/notification/service/SseEventPublisher.java:25-32`: publish 실패 로그. Redis publish 성공은 클라이언트 수신 ACK가 아닙니다.
- `SseEventSubscriber.java:31-55`: 역직렬화 실패 로그 및 로컬 emitter 전송 경로.
- `SSENotificationService.java:65-73,94-100`: “전송 완료” 등 로그 문구만으로 최종 수신을 보장하지 않습니다. publisher가 예외를 내부에서 잡으므로 상위 완료 로그와 publish 실패 로그가 함께 있을 수도 있습니다.
- `NotificationSubscribeService.java:60-69,114-119`: 잘못된 Last-Event-ID/캐시 객체를 건너뛰는 로그. Redis 조회 자체 실패까지 격리하는 것은 아닙니다.

## 5. 별도 승인/검증이 필요한 사항

- 현재 배포 커밋, 활성 blue/green, 프록시 SSE 버퍼링·idle timeout, 실제 healthcheck 결과.
- Docker 로깅 드라이버/회전/디스크 사용량, 민감정보 마스킹과 보존 정책.
- 누락된 Prometheus 설정의 배포 경로, 실제 metrics registry/endpoint, scrape target 상태와 Grafana datasource/dashboard.
- Actuator 외부 접근 제한과 최소 노출 정책. 저장소의 설정이 안전한 운영 구성이라는 뜻은 아닙니다.
- 두 인스턴스 사이 SSE 수신·재연결/replay·Redis 장애 시나리오. 상세 구현/제약은 [SSE 가이드](../notification/SSE_NOTIFICATION_GUIDE.md)를 참고하세요.
