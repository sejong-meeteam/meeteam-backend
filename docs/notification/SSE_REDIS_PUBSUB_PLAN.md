# SSE 멀티 인스턴스 전파(Redis Pub/Sub) 도입 및 기존 버그 수정 구현 계획

> 작성일: 2026-06-15
> 대상: `domain/notification`, `web/notification`, `global/config`
> 목적: 현재 단일 인스턴스에서만 동작하는 SSE 실시간 알림을 **다중 인스턴스(스케일아웃) 환경**에서도 동작하도록 Redis Pub/Sub으로 전파 계층을 추가하고, 동시에 발견된 모든 결함을 수정한다.

---

## 1. 배경 및 현재 아키텍처

### 1.1 현재 구조 요약

| 구성 요소 | 저장소 | 역할 |
|---|---|---|
| `SseEmitter` (연결 객체) | **JVM In-Memory** (`ConcurrentHashMap`) | 실제 클라이언트 SSE 연결 보관 (`EmitterRepositoryImpl.java:101`) |
| 이벤트 캐시 (ZSET) | **Redis** `eventCache:{memberId}` | 재연결 시 재전송용 이벤트 ID 인덱스(시간순) |
| 이벤트 데이터 (String) | **Redis** `eventCache:evt:{eventId}` | 실제 `SseEnvelope` 데이터 (TTL 2h) |

### 1.2 현재 전송 흐름

```
[비즈니스 트랜잭션 커밋]
        │  ApplicationEvent 발행
        ▼
NotificationListener (@Async, @TransactionalEventListener AFTER_COMMIT)
        ▼
SSENotificationService.notify(...)
        ▼
broadcastToReceiver(receiverId, type, payload)
        ├─ saveEventCache(eventId, envelope)              → Redis 저장
        └─ findAllEmitterStartWithByMemberId(receiverId)  → 로컬 메모리 맵 조회
                 └─ emitter.send(...)                      → 로컬 연결에만 전송
```

### 1.3 핵심 한계

`emitter`는 **자신이 떠 있는 JVM 메모리에만** 존재한다. 알림을 발행하는 인스턴스와 클라이언트의 SSE 연결을 들고 있는 인스턴스가 **다르면 실시간 전송이 불가능**하다.

```
사용자 SSE 연결 → 서버 A 메모리
알림 이벤트 발생 → 서버 B (@Async 리스너)
서버 B의 emitter 맵에는 해당 사용자가 없음 → 실시간 전송 실패
(Redis 캐시에는 남지만, 사용자는 재연결 전까지 알림 못 받음)
```

이 한계는 코드 주석 `EmitterRepositoryImpl.java:26`("멀티 인스턴스 환경에서는 각 서버가 자신의 연결만 관리")에도 명시되어 있다.

---

## 2. 발견된 문제점 (수정 대상)

| # | 심각도 | 문제 | 위치 | 근거 |
|---|---|---|---|---|
| P1 | 🔴 치명 | 재연결 시 캐시 이벤트 역직렬화 실패 → **구독 요청 자체가 500** | `RedisConfig.java:29`, `NotificationSubscribeService.java:118` | 아래 2.1 |
| P2 | 🟠 높음 | 멀티 인스턴스 실시간 전파 불가 | `SSENotificationService.java:146` | 1.3 |
| P3 | 🟡 중간 | 같은 밀리초 내 `eventId`/`emitterId` 충돌 → 이벤트·연결 유실 | `SSENotificationService.java:165`, `NotificationSubscribeService.java:76` | 아래 2.2 |
| P4 | 🟡 중간 | 재연결 경계(`afterTs+1`) + P3 결합으로 같은 ms 이벤트 누락 | `EmitterRepositoryImpl.java:281` | 2.2 |
| P5 | 🟢 경미 | 하트비트 부재 → 프록시/LB가 idle 연결 조기 종료 | `NotificationSubscribeService.java:54` | 2.3 |
| P6 | 🟢 경미 | `broadcast` 예외 처리가 `IOException`만 잡음, dead emitter 정리 누락 | `SSENotificationService.java:158` | 2.4 |
| P7 | 🟢 경미 | `createdAt`가 `LocalDate` (시각 정보 유실) | `SSENotificationService.java:143` | 2.5 |

### 2.1 P1 — 직렬화/역직렬화 불일치 (치명)

`RedisConfig.java:29`:
```java
new GenericJackson2JsonRedisSerializer(objectMapper); // Boot 기본 ObjectMapper 주입
```
- `GenericJackson2JsonRedisSerializer(ObjectMapper)` 생성자는 **넘겨받은 mapper를 그대로** 사용하고 default typing(`@class`)을 켜지 않는다. (인자 없는 `GenericJackson2JsonRedisSerializer()`만 `@class`를 자동 활성화한다.)
- 주입되는 것은 Spring Boot 자동 구성 `ObjectMapper`이며, 코드베이스 어디에도 `activateDefaultTyping` 설정이 없다(검증 완료).
- 따라서 `SseEnvelope` 저장 시 `@class`가 빠지고, 다시 읽을 때 **`LinkedHashMap`으로 역직렬화**된다.

그 결과 `NotificationSubscribeService.java:116-125`:
```java
private void sendReplay(SseEmitter emitter, String eventId, Object data) {
    try {
        if (data instanceof SseEnvelope<?> env && env.getType() != null) { // ← 항상 false
            ...
        } else {
            throw new CustomException(ErrorCode.NO_MATCHING_TYPE); // RuntimeException
        }
    } catch (IOException ex) { ... } // RuntimeException은 못 잡음
}
```
- `data`는 `LinkedHashMap`이므로 항상 `NO_MATCHING_TYPE`(RuntimeException) 발생.
- `try/catch`는 `IOException`만 잡으므로 예외가 `subscribe()`의 `cachedEvents.forEach(...)` 밖으로 전파 → **`/api/v1/subscribe` 요청 전체 실패**.
- 즉 캐시된 이벤트가 1건이라도 있는 상태에서 `Last-Event-ID`로 재연결하면 SSE 연결 자체가 끊긴다. SSE의 핵심인 재연결 복구가 통째로 망가져 있다.

추가로 `SseEnvelope`(`SseEnvelope.java`)는 `final` 필드 + `@AllArgsConstructor`/`@Builder`에 `@JsonCreator`/no-arg 생성자가 없어, `@class`가 있어도 역직렬화가 컴파일 `-parameters` 플래그에 의존한다. 견고성을 위해 명시적 생성자 바인딩이 필요하다.

### 2.2 P3/P4 — eventId 충돌과 순서 보장 부재

`makeEventId`/`makeEmitterId` 모두 `memberId + "_" + System.currentTimeMillis()`:
- 동일 수신자에게 1ms 안에 알림 2건 → **동일 `eventId`**.
  - `saveEventCache`(`EmitterRepositoryImpl.java:207`)의 ZSET `add`는 member 중복으로 갱신, String `set`은 덮어쓰기 → **한 건이 캐시에서 소실**.
  - live `id`도 동일해져 클라이언트가 구분 불가.
- `emitterId` 충돌: 한 사용자가 같은 ms에 탭 2개 열면 `emitters.put` 덮어쓰기 → 먼저 연결된 emitter가 맵에서 사라지고 정리 콜백도 꼬임.
- 재연결 경계: 캐시 조회가 `afterTs + 1`(`EmitterRepositoryImpl.java:281`)이라 같은 timestamp의 두 번째 이벤트는 영구 누락.
- **멀티 인스턴스에서는 더 심각**: `System.currentTimeMillis()`는 서버 간 시계 오차(clock skew)가 있어, 발행 인스턴스가 달라지면 순서가 뒤집히거나 score가 겹친다. **단일 시계 소스**가 필요하다.

### 2.3 P5 — 하트비트 부재
연결 시 `PING` 1회(`NotificationSubscribeService.java:54`)만 전송. Nginx/ALB 등은 기본 idle 타임아웃(보통 60초)에 연결을 끊을 수 있고, 그러면 P1 재연결 버그를 그대로 밟는다.

### 2.4 P6 — 불완전한 예외 처리
`broadcastToReceiver`의 `catch (IOException e)`(`SSENotificationService.java:158`)는 이미 완료된 emitter에 `send` 시 발생하는 `IllegalStateException`을 못 잡는다. 상위 `try/catch(Exception)`가 삼켜서 크래시는 없으나, 죽은 emitter가 이 경로에서 정리되지 않는다.

### 2.5 P7 — createdAt 타입
`createdAt(LocalDate.now())`는 날짜만 저장. 정렬은 eventId 기준이라 기능엔 무해하나 `LocalDateTime`이 적절.

---

## 3. 목표 / 비목표

### 목표
1. 다중 인스턴스에서 알림이 **연결을 들고 있는 인스턴스로 전파**되어 실시간 전송된다. (P2)
2. 재연결 replay가 정상 동작한다. (P1, P3, P4)
3. 이벤트 ID가 **전역적으로 유니크하며 단조 증가**한다. (P3, P4)
4. 운영 환경에서 연결이 안정적으로 유지된다. (P5)
5. 전송 실패가 정확히 정리·격리된다. (P6)

### 비목표 (이번 범위 아님)
- 알림의 **정확히 한 번(exactly-once)** 전달 보장. Redis Pub/Sub은 fire-and-forget(at-most-once)이며, 끊긴 사이의 누락은 **재연결 시 Redis 이벤트 캐시 replay로 보완**한다(현 설계 유지).
- 알림 DB 영속화 로직 변경(이미 비즈니스 레이어에서 처리됨).
- 인증/권한 구조 변경.

---

## 4. 설계: Redis Pub/Sub 멀티 인스턴스 전파

### 4.1 핵심 아이디어

알림 발행 시 emitter에 **직접 전송하지 않고** Redis 채널에 publish한다. 모든 인스턴스가 동일 채널을 구독하고, 메시지를 받으면 **자신의 로컬 emitter 맵에 해당 수신자가 있을 때만** 전송한다.

```
              publish "sse:events"
SSENotificationService ───────────────► Redis Channel
                                              │ (모든 구독 인스턴스에 fan-out)
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                          ▼
              서버 A 구독자             서버 B 구독자              서버 C 구독자
       (receiverId 연결 보유)       (연결 없음 → skip)        (연결 없음 → skip)
                    ▼
            로컬 emitter.send(...)  → 클라이언트 수신
```

- 발행 인스턴스도 자신이 구독자이므로, 자기 자신이 연결을 들고 있으면 동일 경로로 전송된다. → **분기 없는 단일 코드 경로**.

### 4.2 채널 설계와 근거

**단일 채널 `sse:events` + 메시지 내 `receiverId` 포함** 방식을 채택한다.

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **단일 채널 + receiverId 필터** | 구독 1건으로 고정, 연결 변동에 무관, 운영 단순 | 모든 인스턴스가 모든 메시지 수신(필터링 비용) | ✅ |
| per-user 채널 `sse:user:{id}` | 인스턴스가 관심 사용자만 수신 | 사용자 연결/해제마다 동적 subscribe/unsubscribe 필요 → 복잡, 경합 | ❌ |

근거: 알림 트래픽은 인스턴스당 초당 수천 건 수준이 아니며(팀 매칭 도메인), 메시지당 `receiverId` 비교는 O(1)에 가깝다. 동적 구독 관리의 복잡성·버그 위험이 필터링 비용보다 훨씬 크다. **단순함이 정확성을 높인다.**

### 4.3 메시지 포맷

```java
// web/notification/SsePublishMessage.java (신규)
public record SsePublishMessage(
        Long receiverId,      // 어느 사용자에게
        String eventId,       // "{memberId}_{seq}"
        SseEnvelope<Object> envelope  // 실제 페이로드
) {}
```
- 메시지에 `eventId`를 포함시켜 live 전송과 캐시의 ID를 일치시킨다(재연결 시 Last-Event-ID 정합성).

### 4.4 발행/구독 책임 분리

| 컴포넌트 | 변경 | 책임 |
|---|---|---|
| `SSENotificationService` | 수정 | eventId 생성 → 캐시 저장 → **publish만** 수행 (직접 send 제거) |
| `SseEventPublisher` (신규) | 신규 | `RedisTemplate.convertAndSend(CHANNEL, message)` 래핑 |
| `SseEventSubscriber` (신규) | 신규 | `MessageListener` 구현. 메시지 수신 → 로컬 emitter 조회 → send/정리 |
| `RedisPubSubConfig` (신규) | 신규 | `RedisMessageListenerContainer` + 채널 토픽 등록 |
| `EmitterRepository(Impl)` | 유지 | emitter 맵/캐시 그대로 (전송 로직만 Subscriber로 이동) |

### 4.5 전체 흐름 (변경 후)

```
NotificationListener (@Async, AFTER_COMMIT)
        ▼
SSENotificationService.notify(...)
        ▼
broadcastToReceiver(receiverId, type, payload)
        1. eventId = nextEventId(receiverId)         // Redis INCR (4.6 / 6.2)
        2. envelope = SseEnvelope.of(type, payload, now())
        3. emitterRepository.saveEventCache(eventId, envelope)   // Redis
        4. ssePublisher.publish(new SsePublishMessage(receiverId, eventId, envelope))
                 │ Redis "sse:events"
                 ▼
SseEventSubscriber.onMessage(...)  [모든 인스턴스에서 실행]
        - emitters = findAllEmitterStartWithByMemberId(receiverId)
        - emitters 비어있으면 return (이 인스턴스엔 연결 없음)
        - 각 emitter.send(id=eventId, name=type, data=envelope)
        - 실패 시 completeWithError + deleteById
```

---

## 5. 문제별 수정 방안

### 5.1 P1 직렬화 수정 — `@class` 타입 정보 + 보안 제한

`RedisConfig`에서 **default typing을 켠 전용 ObjectMapper**를 만들어 `redisObjectTemplate`과 Pub/Sub 직렬화에 사용한다.

```java
// 보안: 전역 default typing은 역직렬화 가젯 취약점이 있으므로,
// 우리 패키지로 제한한 PolymorphicTypeValidator 사용
PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.wardk.meeteam_backend.")
        .allowIfSubType("java.util.")
        .allowIfSubType("java.time.")
        .build();

ObjectMapper redisMapper = objectMapper.copy()
        .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(redisMapper);
```
- 근거: 인자 없는 `GenericJackson2JsonRedisSerializer()`도 `@class`를 켜지만 우리 `ObjectMapper`의 모듈(JavaTimeModule 등)을 못 쓴다. `objectMapper.copy()`로 모듈을 유지하면서 typing만 추가하는 것이 정석.
- **보안 근거**: `LaissezFaireSubTypeValidator`(무제한)는 알려진 RCE 가젯 위험이 있으므로, 허용 패키지를 우리 도메인/표준 라이브러리로 한정한다.
- 폴리모픽 `data`(`Payload` 인터페이스)도 `@class`로 정확히 라운드트립된다.

**추가로 `SseEnvelope`에 Jackson 역직렬화 생성자 명시** (P1의 견고성 보강):
```java
@JsonCreator
public SseEnvelope(
        @JsonProperty("type") NotificationType type,
        @JsonProperty("data") T data,
        @JsonProperty("createdAt") LocalDateTime createdAt) { ... }
```
- 근거: `-parameters` 컴파일 플래그 의존성을 제거하여 빌드 설정과 무관하게 안정적으로 역직렬화한다.

**그리고 `sendReplay`의 예외를 방어적으로 처리**: 역직렬화 실패/타입 불일치 1건이 전체 구독을 깨뜨리지 않도록, 해당 이벤트만 skip하고 로깅한다.

### 5.2 P3/P4 eventId — Redis INCR 기반 전역 단조 증가 시퀀스

`eventId = "{memberId}_{seq}"`, `seq = INCR eventSeq:{memberId}` (Redis).

```java
// EmitterRepository에 추가
long nextSequence(Long memberId);   // stringRedisTemplate.opsForValue().increment("eventSeq:" + memberId)
```
- ZSET score = `seq`(단조 증가 정수), member = `eventId`.
- 재연결: `Last-Event-ID = "{memberId}_{seq}"` → `extractSeq` 파싱 → `rangeByScore(seq+1, +inf)` → **정확히 그 이후만**, 누락·중복 없음.
- 근거:
  - **유니크**: INCR는 원자적이라 동일 ms 충돌이 원천 차단된다(P3 해결).
  - **순서**: Redis 단일 시계가 모든 인스턴스의 순서를 정의 → clock skew 무관(P2 멀티 인스턴스의 순서 정합성 확보).
  - **경계 정확**: 정수 시퀀스라 `seq+1` 경계가 정확(P4 해결).
- `eventSeq:{memberId}` 키에도 TTL(이벤트 캐시보다 길게, 예: 그대로 두거나 7일)을 둬 메모리 누수 방지. (시퀀스가 리셋돼도 캐시 TTL이 2h이므로 충돌 가능성 사실상 없음 — 별도 검토 항목)

`emitterId`는 순서가 불필요하므로 `"{memberId}_{UUID}"`로 유니크만 보장(P3의 emitter 충돌 해결).

### 5.3 P5 하트비트 — 주기적 PING 스케줄러

```java
// SseHeartbeatScheduler (신규)
@Scheduled(fixedRate = 30_000)   // 30초
public void heartbeat() {
    emitterRepository.forEachEmitter((id, emitter) -> trySendComment(id, emitter));
}
```
- 근거: 30초 주기는 대부분 LB 기본 idle(60초)보다 짧아 연결 유지에 충분하다. SSE 주석(`:ping`)이나 `PING` 이벤트를 보낸다.
- `@EnableScheduling` 활성화 필요(이미 있으면 재사용).
- 전송 실패 시 즉시 정리하여 dead emitter 자연 소거(P6와 시너지).

### 5.4 P6 예외 처리/정리 통합

emitter 전송 공통 유틸로 통합하고 `catch (Exception)`으로 넓혀 `IllegalStateException`까지 포착, 실패 시 `completeWithError` + `deleteById`. live 전송(Subscriber), 하트비트, replay 모두 이 경로를 공유.

### 5.5 P7 createdAt
`SseEnvelope.createdAt`을 `LocalDateTime`으로 변경. (5.1의 `@JsonCreator`와 함께 반영.)

---

## 6. 변경 / 신규 파일

### 신규
| 파일 | 역할 |
|---|---|
| `global/config/RedisPubSubConfig.java` | `RedisMessageListenerContainer`, 채널 토픽, 리스너 등록 |
| `web/notification/SsePublishMessage.java` | Pub/Sub 메시지 DTO(record) |
| `domain/notification/service/SseEventPublisher.java` | 채널 publish 래퍼 |
| `domain/notification/service/SseEventSubscriber.java` | `MessageListener` — 수신 후 로컬 전송 |
| `domain/notification/service/SseEmitterSender.java`(선택) | emitter 전송/정리 공통 유틸 |
| `global/config/SseHeartbeatScheduler.java` | 주기적 하트비트 |

### 수정
| 파일 | 변경 |
|---|---|
| `global/config/RedisConfig.java` | typing ObjectMapper + 보안 PTV 적용 |
| `web/notification/SseEnvelope.java` | `createdAt`→`LocalDateTime`, `@JsonCreator` 추가 |
| `domain/notification/service/SSENotificationService.java` | 직접 send 제거 → publish 호출, eventId=INCR |
| `domain/notification/service/NotificationSubscribeService.java` | `emitterId`=UUID, `extractSeq` 파싱, `sendReplay` 방어적 처리 |
| `domain/notification/repository/EmitterRepository(Impl)` | `nextSequence`, `forEachEmitter` 추가, score=seq |
| 메인 애플리케이션 클래스 | `@EnableScheduling`(없을 경우) |

---

## 7. 단계별 구현 순서 (점진적·검증 가능)

> 각 단계는 독립적으로 빌드·배포 가능하도록 순서를 잡았다.

1. **직렬화 수정 (P1, P7)** — `RedisConfig` typing + `SseEnvelope` 개선. 기존 단일 인스턴스 replay 즉시 정상화. 가장 시급.
2. **eventId 시퀀스 (P3, P4)** — `EmitterRepository.nextSequence`, score/파싱 변경. replay 정확성 확보.
3. **예외 처리/정리 통합 (P6)** — `SseEmitterSender` 공통화.
4. **Pub/Sub 전파 (P2)** — Publisher/Subscriber/Config 추가, `SSENotificationService`를 publish로 전환.
5. **하트비트 (P5)** — 스케줄러 추가.
6. **테스트 & 검증** — 8장.

---

## 8. 설정 변경

`application-*.yml`의 Redis는 이미 구성됨(`application-prod.yml:30` `data.redis.host: redis`). **추가 인프라 불필요** — Pub/Sub은 기존 Redis 인스턴스를 그대로 사용한다(별도 채널 사전 생성 불필요).

운영 점검: Redis `ConnectionFactory`가 Pub/Sub 동시 사용 시 충분한지 확인. Lettuce(Boot 기본)는 단일 커넥션으로 pub/sub + 일반 명령을 분리 처리하므로 문제없음. `RedisMessageListenerContainer`는 전용 스레드풀(`TaskExecutor`)을 사용하도록 구성한다.

---

## 9. 테스트 계획

| 레벨 | 시나리오 |
|---|---|
| 단위 | `SseEventSubscriber`: receiverId 연결 보유/미보유 시 send 호출 여부 |
| 단위 | `EmitterRepositoryImpl`: `nextSequence` 단조 증가, `findEventCacheAfter` 경계(seq+1) |
| 단위 | `SseEnvelope` 직렬화→역직렬화 라운드트립(`@class`, polymorphic `Payload`, `LocalDateTime`) |
| 통합 | embedded Redis(or testcontainers)로 publish→subscribe→로컬 emitter 수신 |
| 통합 | replay: 캐시 N건 저장 후 `Last-Event-ID`로 재연결 시 (seq 이후)만 수신 |
| 수동 | 2개 인스턴스(서버 A 연결 / 서버 B 발행) → A의 클라이언트가 실시간 수신 |

---

## 10. 리스크 / 운영 고려사항

| 리스크 | 영향 | 완화 |
|---|---|---|
| Redis 장애 | publish 실패 → 실시간 전송 중단 | 캐시 replay로 복구 가능. publish는 try/catch로 격리(DB·트랜잭션엔 영향 없음) |
| at-most-once 전달 | 발행 순간 연결 끊긴 사용자는 그 순간 누락 | 재연결 시 Redis 캐시 replay가 메움(설계상 의도) |
| 역직렬화 보안 | default typing 가젯 공격 | `PolymorphicTypeValidator`로 허용 패키지 제한(5.1) |
| 메시지 폭증 | 모든 인스턴스가 모든 메시지 수신 | 현 트래픽 규모에서 무시 가능. 필요 시 per-user 채널로 후속 전환 |
| 기존 `redisObjectTemplate` 사용처 영향 | typing 변경이 다른 소비자에 영향 | **검증 완료**: 해당 빈 사용처는 `EmitterRepositoryImpl` 단독 |

### 롤백
각 단계가 독립적이므로 Pub/Sub 단계(4)만 되돌리면 기존 단일 인스턴스 직접 전송으로 회귀 가능(단, 1~3단계 버그 수정은 유지 권장).

---

## 11. 향후 개선 (이번 범위 밖)
- per-user 채널 전환(트래픽 증가 시).
- 알림 전달 확인(ACK)/재시도.
- Redis Streams 기반 영속 이벤트 로그로 캐시 대체.
- `SseEnvelope`/`Payload` 스키마 버저닝.