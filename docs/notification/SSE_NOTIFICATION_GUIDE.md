# SSE 알림 시스템, 처음부터 이해하기

> 이 문서는 "코드가 전혀 이해 안 되는 상태"에서 출발해, **비유 → 전체 그림 → 시나리오 → 파일별 역할** 순서로 따라가며 이해하는 것을 목표로 합니다.
> (기술적 결정/근거가 궁금하면 옆 문서 [`SSE_REDIS_PUBSUB_PLAN.md`](./SSE_REDIS_PUBSUB_PLAN.md)를 보세요. 이 문서는 "어떻게 동작하나"에 집중합니다.)

---

## 0. 한 문장 요약

> **사용자가 브라우저로 "알림 받을 전화선"을 하나 깔아두면(SSE 연결), 서버에서 알림이 생길 때마다 그 전화선으로 실시간으로 밀어 넣어주는 시스템**입니다.

---

## 1. 먼저 SSE가 뭔가요? (개념)

평소 API는 이렇게 동작합니다:

```
클라이언트: "알림 목록 줘"  ──요청──▶  서버
클라이언트:        ◀──응답──         서버: "여기 있어" (끝. 연결 닫힘)
```

이건 **클라이언트가 물어볼 때만** 답을 줍니다. 그런데 알림은 *서버에서 먼저* 보내야 하죠("누가 너 프로젝트에 지원했어!"). 그래서 **연결을 끊지 않고 계속 열어두는** 기술이 필요한데, 그게 **SSE(Server-Sent Events)** 입니다.

```
클라이언트: "나 알림 구독할게"  ──요청──▶  서버
클라이언트:     ◀════ 연결 계속 열려있음 ════  서버
                 ◀── "지원 알림!"   (서버가 아무 때나)
                 ◀── "승인 알림!"   (서버가 아무 때나)
                 ◀── "프로젝트 종료!"
```

> 📞 **비유:** SSE 연결은 **"서버 → 사용자 방향으로만 말할 수 있는 전화선"** 입니다. 사용자는 전화를 걸어 선만 깔아두고(구독), 그 다음부터는 서버가 일방적으로 말을 겁니다.

이 "전화선" 객체가 코드에서 **`SseEmitter`** 입니다. 사용자 1명이 탭을 3개 열면 전화선도 3개 생깁니다.

---

## 2. 등장인물 (클래스를 사람/사물로 비유)

> 💡 클래스명으로 코드를 검색하면 바로 찾을 수 있습니다. 패키지는 모두 `com.wardk.meeteam_backend.` 아래입니다.

| 클래스 (검색용) | 비유 | 하는 일 | 위치 |
|---|---|---|---|
| `SseEmitter` | **전화선** | 서버 ↔ 브라우저 사이 열린 연결 1개 | *(Spring 내장 클래스)* |
| `NotificationController` | **접수 창구** | 사용자가 "구독할게" 하고 들어오는 입구 (`/api/v1/subscribe`) | `web/notification/controller/` |
| `NotificationSubscribeService` | **전화선 설치 기사** | 전화선(emitter)을 깔고, 끊긴 동안 못 받은 알림을 다시 틀어줌 | `domain/notification/service/` |
| `EmitterRepository` / `EmitterRepositoryImpl` | **전화선 보관함 + 사물함** | 누가 어떤 전화선을 쓰는지 메모리에 보관 / 알림 사본은 Redis에 보관 | `domain/notification/repository/` |
| `SSENotificationService` | **알림 작성자** | "이런 알림 보내야 해!"가 생기면 알림을 만들어서 방송실로 넘김 | `domain/notification/service/` |
| `SseEventPublisher` | **방송실 송신기** | 알림을 Redis 방송 채널에 쏨 | `domain/notification/service/` |
| `SseEventSubscriber` | **각 지점의 수신기** | 방송을 듣고, "이 사용자 전화선이 우리 지점에 있네?" 하면 전화선으로 전달 | `domain/notification/service/` |
| `SseEmitterSender` | **전송 담당 + 청소부** | 실제로 전화선에 말 넣고, 끊긴 전화선은 치워줌 | `domain/notification/service/` |
| `SseHeartbeatScheduler` | **30초마다 "여보세요?"** | 전화선이 안 끊기게 주기적으로 신호 보냄 | `domain/notification/service/` |
| `NotificationListener` | **알림 발송 신호 수신기** | DB 커밋 후 "알림 보내!" 이벤트를 비동기로 받음 | `domain/notification/` |
| `SseEnvelope` | **알림 봉투** | 브라우저가 최종으로 받는 알림 내용물 | `web/notification/` |
| `SsePublishMessage` | **방송용 겉포장** | 서버끼리 방송할 때 쓰는 메시지(주소 + 봉투) | `web/notification/` |
| `RedisConfig` / `RedisPubSubConfig` | **Redis 설정** | 객체 저장/복원 방식, 방송 채널 구독 설정 | `global/config/` |
| `Redis` | **중앙 우체국 + 방송국** | (1) 알림 사본 보관, (2) 서버들 사이 방송 중계 | *(외부 인프라)* |

---

## 3. Redis가 두 가지 역할을 한다 (여기가 제일 헷갈리는 부분!)

이 시스템에서 **Redis는 완전히 다른 두 가지 용도**로 쓰입니다. 이걸 구분하면 절반은 이해한 겁니다.

```
Redis 용도 ①  "알림 사본 보관함" (캐시)
   - 보낸 알림을 잠깐(2시간) 저장해둠
   - 사용자가 잠깐 끊겼다 다시 들어오면, 그동안 못 받은 알림을 여기서 꺼내 다시 틀어줌

Redis 용도 ②  "방송국" (Pub/Sub)
   - 서버가 여러 대일 때, "이 알림 받을 사람 어디 붙어있어?"를 모르니까
   - 일단 방송으로 전체에 쏘고, 그 사람 전화선을 가진 서버가 받아서 전달
```

⚠️ 반대로, **전화선(`SseEmitter`) 자체는 Redis에 못 넣습니다.** 전화선은 "지금 이 서버 메모리에 살아있는 연결"이라 다른 서버로 옮길 수 없어요. 그래서 전화선은 **각 서버의 메모리(`ConcurrentHashMap`)에만** 있습니다.

---

## 4. 시나리오로 따라가기

### 시나리오 A — 사용자가 알림을 켠다 (구독)

```
[브라우저] "나 알림 구독할게"  →  GET /api/v1/subscribe
     │
     ▼
NotificationController.subscribe()           (접수 창구)
     │
     ▼
NotificationSubscribeService.subscribe()     (설치 기사)
     │
     ├─ 1. 새 전화선(SseEmitter) 생성
     ├─ 2. 보관함에 저장   emitterRepository.save("사용자5_uuid", 전화선)
     ├─ 3. "연결됐어요" 신호 1번 보냄 (PING)
     └─ 4. 끊겼다 들어온 거면 → 못 받은 알림 다시 틀어줌 (시나리오 D)
     │
     ▼
[브라우저]  ◀═══ 전화선 연결 유지 ═══  이제부터 알림 기다림
```

여기서 보관함 열쇠(키)는 `"사용자ID_랜덤UUID"` 형태입니다. 같은 사용자가 탭을 여러 개 열어도 UUID가 달라서 안 겹칩니다.

---

### 시나리오 B — 알림이 발생해서 전달된다 (서버 1대일 때)

예: 누군가 내 프로젝트에 지원함 → 나에게 알림.

```
[프로젝트 지원 처리 끝, DB 커밋 완료]
     │ "알림 보내!" 이벤트 발생
     ▼
NotificationListener   (커밋 후 비동기로 실행)
     │
     ▼
SSENotificationService.notify()              (알림 작성자)
     │
     ├─ 1. 누구한테/무슨 알림인지 정리해서 "봉투(SseEnvelope)"에 담음
     ├─ 2. 봉투 사본을 Redis 보관함에 저장 (나중 재연결 대비)
     └─ 3. 방송실에 넘김 → SseEventPublisher.publish()
     │
     ▼
   Redis 방송 채널 "sse:events"  📢
     │
     ▼
SseEventSubscriber.onMessage()   (수신기 — 모든 서버가 듣고 있음)
     │
     ├─ "이 알림 받을 사람(사용자5) 전화선이 우리 서버에 있나?"
     ├─ 있다! → SseEmitterSender 가 전화선으로 봉투 전달
     ▼
[브라우저]  ◀── "지원 알림 도착!" 🔔
```

> 💡 서버가 1대여도 방송을 거칩니다. "방송 → 내가 듣고 → 내가 전달"이라 좀 빙 도는 것 같지만, **서버가 여러 대로 늘어나도 코드가 똑같이 동작**하게 하려고 일부러 이렇게 만들었습니다.

---

### 시나리오 C — 서버가 여러 대일 때 (이게 핵심 개선!)

문제 상황: 사용자는 **서버 A**에 전화선을 깔았는데, 알림은 **서버 B**에서 생김.

**예전 코드(버그):**
```
사용자5 전화선 → 서버 A 메모리
알림 발생 → 서버 B
서버 B: "내 메모리에 사용자5 전화선 없는데?" → 전달 실패 ❌
```

**지금 코드(Redis 방송으로 해결):**
```
알림 발생 → 서버 B
서버 B: Redis 채널에 방송 📢 "사용자5에게 이 알림!"
        │
   ┌────┴───────────────┬───────────────┐
   ▼                    ▼               ▼
서버 A 수신기        서버 B 수신기     서버 C 수신기
"사용자5 있다!" ✅    "없다, 패스"      "없다, 패스"
   │
   ▼
사용자5 전화선으로 전달 🔔
```

방송은 **모든 서버**가 듣지만, 실제 전달은 **그 사용자의 전화선을 가진 서버만** 합니다. 나머지는 그냥 무시해요. 이게 "멀티 인스턴스 전파(Redis Pub/Sub)"의 전부입니다.

---

### 시나리오 C 심화 — Redis가 메시지를 어떻게 "밀어주나"(push)?

> "방송을 듣는다"는 게 코드로는 정확히 무슨 일일까요? `SseEventSubscriber`는 어떻게 가만히 있다가 메시지를 받을까요? 여기서 한 겹 더 파봅니다.

#### ① `implements MessageListener`는 그냥 "약속"일 뿐

```java
public class SseEventSubscriber implements MessageListener {
    public void onMessage(Message message, byte[] pattern) { ... }
}
```

`implements MessageListener`는 **"나는 `onMessage`라는 메서드가 있어요"** 라는 약속(인터페이스)일 뿐, 이걸 구현했다고 저절로 호출되지 않습니다. 누군가 **"이 객체를 Redis 채널에 연결해줘"** 라고 등록해줘야 합니다.

> 📌 비유: `onMessage`는 "초인종 누르면 나와요"라고 적어둔 문패. 하지만 **초인종 배선**을 깔지 않으면 아무리 눌러도 안 울립니다.

#### ② 진짜 엔진: `RedisMessageListenerContainer` (`global/config/RedisPubSubConfig.java`)

그 "배선"을 까는 게 이 한 줄입니다:

```java
container.addMessageListener(sseEventSubscriber, sseChannelTopic);
// "sse:events 채널에 메시지 오면 → sseEventSubscriber.onMessage() 불러줘"
```

앱이 시작될 때 이 컨테이너가 모든 저수준 작업을 대신합니다:

```
앱 시작
  │
  ▼
RedisMessageListenerContainer 가 자동으로 start()
  │
  ├─ 1. Redis 연결 1개를 "구독 전용"으로 빌림 (일반 명령용과 분리)
  ├─ 2. 그 연결로 Redis 서버에 명령:   SUBSCRIBE sse:events
  │      → Redis: "OK, 이 클라이언트는 sse:events 구독자로 기억"
  └─ 3. 백그라운드 스레드가 그 연결을 붙잡고
         소켓에 데이터가 올 때까지 블로킹하며 계속 읽음 (read loop)
```

핵심은 **연결을 끊지 않고 계속 열어둔다**는 점. SSE에서 브라우저와 전화선을 열어두는 것과 똑같은 원리를, 이번엔 **서버 ↔ Redis** 사이에서 합니다.

#### ③ 누군가 `PUBLISH` 하면 Redis가 먼저 밀어준다

발행 쪽(`SseEventPublisher`)의 `convertAndSend("sse:events", msg)`는 사실상 이 명령입니다:

```
PUBLISH sse:events <직렬화된 바이트들>
```

그러면 **Redis 서버가 주체가 되어**, `sse:events`를 구독 중인 모든 연결로 그 바이트를 밀어 넣습니다(push). 우리가 "새 거 있어?"를 주기적으로 물어보는(polling) 게 **아닙니다.**

#### ④ 그 push가 `onMessage`까지 오는 길

②에서 블로킹하며 읽던 백그라운드 스레드가 깨어납니다:

```
Redis 서버가 소켓으로 바이트 push
        │
        ▼
컨테이너의 구독 스레드가 소켓에서 바이트를 읽음
        │
        ├─ 받은 바이트를 Message 객체로 포장
        │     - message.getChannel() = "sse:events"
        │     - message.getBody()    = <직렬화된 바이트들>   ← onMessage에서 deserialize 하는 그것
        │
        ├─ "이 채널에 등록된 리스너 누구지?" → sseEventSubscriber 발견
        │
        ▼
sseEventSubscriber.onMessage(message, pattern)  ← 드디어 호출! 🎉
```

즉 **`onMessage`를 부르는 주체는 우리 코드가 아니라 컨테이너의 백그라운드 스레드**입니다. 우리는 "메시지 오면 이렇게 처리해줘"라는 내용물만 채워둔 거예요.

#### ⑤ `onMessage`의 두 인자

```java
public void onMessage(Message message, byte[] pattern)
```
- **`message`** — 방금 도착한 메시지. `message.getBody()`가 발행자가 보낸 원본 바이트라서 다시 객체로 복원합니다:
  ```java
  payload = (SsePublishMessage) redisValueSerializer.deserialize(message.getBody());
  ```
  (발행할 때 쓴 직렬화기와 **같은** 것으로 풀어야 모양이 맞음 → `@class` 타입정보가 중요했던 이유. 6장 Q&A 참고)
- **`pattern`** — `SUBSCRIBE`(정확한 채널명) 대신 `PSUBSCRIBE`(`sse:*` 같은 패턴 구독)를 썼을 때 매칭된 패턴. 우리는 정확한 채널명으로 구독해 거의 안 씁니다.

#### ⑥ 한 가지 주의점 (스레드)
컨테이너는 기본적으로 **메시지 수신 스레드에서 곧바로 `onMessage`를 실행**합니다. 그래서 `onMessage` 안에서 무거운 작업(긴 DB 호출 등)을 하면 그 사이 다른 메시지가 밀립니다. 우리 `onMessage`는 **메모리 맵 조회 + 전송**만 하는 가벼운 작업이라 괜찮습니다. 나중에 무거워지면 `container.setTaskExecutor(...)`로 처리 스레드를 분리하면 됩니다.

> 🔍 더 파보고 싶다면 실제 Spring 클래스를 열어보세요 — 다 평범한 코드입니다:
> - `RedisMessageListenerContainer` — `doStart()` / `addMessageListener()` / 내부 `SubscriptionTask`
> - `RedisConnection.subscribe(MessageListener, byte[]... channels)` — 실제 `SUBSCRIBE` 명령
> - `LettuceSubscription` — Lettuce 드라이버가 소켓을 붙잡고 메시지를 위로 전달하는 부분

---

### 시나리오 D — 잠깐 끊겼다 다시 붙는다 (재연결/replay)

지하철 터널처럼 네트워크가 잠깐 끊기면 전화선이 죽습니다. 그 사이 온 알림은? → **Redis 보관함에서 다시 꺼내줍니다.**

이때 필요한 게 **이벤트 번호(eventId)** 입니다.

```
알림마다 번호표를 붙입니다:  "사용자5_1", "사용자5_2", "사용자5_3" ...
                                         └ 이 숫자(seq)는 Redis가 1, 2, 3... 순서대로 발급

사용자가 3번까지 받고 끊김 → 브라우저가 "마지막으로 받은 건 3번"이라고 기억(Last-Event-ID)

다시 연결할 때:
[브라우저] "구독할게. 참고로 나 3번까지 받았어"
     │
     ▼
NotificationSubscribeService
     │
     └─ Redis 보관함: "3번보다 큰 거(4번, 5번...) 다 줘"
              → 못 받은 4번, 5번을 다시 전화선으로 틀어줌 ✅
```

> 🔢 **왜 번호를 시간 대신 Redis가 발급하나?**
> 예전엔 번호를 "현재 시각(밀리초)"으로 만들었는데, 같은 순간에 알림 2개가 오면 **번호가 똑같아져서 하나가 사라졌습니다.** 또 서버가 여러 대면 서버끼리 시계가 미세하게 달라 순서가 꼬였습니다.
> 그래서 **Redis에게 "다음 번호 뭐야?"라고 물어 1, 2, 3...을 받습니다.** Redis 한 곳에서만 번호를 매기니 절대 안 겹치고 순서도 정확합니다.

---

## 5. 파일별 역할 한눈에

```
요청이 들어오는 쪽 (구독)
├─ NotificationController          접수 창구 (/api/v1/subscribe)
│     web/notification/controller/NotificationController.java
└─ NotificationSubscribeService    전화선 설치 + 끊긴 동안 못 받은 알림 재생
      domain/notification/service/NotificationSubscribeService.java

알림을 보내는 쪽
├─ NotificationListener            "알림 보내!" 신호를 받는 곳 (DB 커밋 후 비동기)
│     domain/notification/NotificationListener.java
├─ SSENotificationService          알림 내용을 만들어 봉투에 담고 방송실로 넘김
│     domain/notification/service/SSENotificationService.java
├─ SseEventPublisher               Redis 채널에 방송 쏨        ┐
│     domain/notification/service/SseEventPublisher.java       │ ← 멀티서버 전파
└─ SseEventSubscriber              방송 듣고 내 서버 전화선에 전달 ┘
      domain/notification/service/SseEventSubscriber.java

전송 공통 도구
├─ SseEmitterSender                실제 전송 + 끊긴 전화선 청소
│     domain/notification/service/SseEmitterSender.java
└─ SseHeartbeatScheduler           30초마다 신호 보내 연결 유지
      domain/notification/service/SseHeartbeatScheduler.java

데이터 보관
├─ EmitterRepository / ...Impl     전화선(메모리) + 알림 사본/번호(Redis) 관리
│     domain/notification/repository/EmitterRepository.java
│     domain/notification/repository/EmitterRepositoryImpl.java
├─ SseEnvelope                     브라우저가 받는 "봉투"
│     web/notification/SseEnvelope.java
└─ SsePublishMessage               서버끼리 방송하는 "겉포장"
      web/notification/SsePublishMessage.java

설정
├─ RedisConfig                     Redis에 객체 저장/복원하는 방식 설정
│     global/config/RedisConfig.java
└─ RedisPubSubConfig               Redis 방송 채널 구독 설정
      global/config/RedisPubSubConfig.java
```

> 위 경로는 모두 `src/main/java/com/wardk/meeteam_backend/` 아래입니다.
> (예: `domain/notification/service/SseEventPublisher.java`
> → `src/main/java/com/wardk/meeteam_backend/domain/notification/service/SseEventPublisher.java`)

---

## 6. 자주 헷갈리는 점 Q&A

**Q. 봉투(`SseEnvelope`)랑 방송 메시지(`SsePublishMessage`)는 뭐가 다른가요?**
- `SseEnvelope` = **알림 내용물** (무슨 타입 알림인지 + 데이터 + 만든 시각). 브라우저가 최종으로 받는 것.
- `SsePublishMessage` = **방송용 겉포장**. 봉투를 감싸서 "누구(receiverId)에게, 몇 번(eventId) 알림"인지 주소를 적은 것. 서버끼리 방송할 때만 씁니다.

**Q. 왜 전화선은 Redis에 안 넣어요?**
- 전화선(`SseEmitter`)은 "지금 이 서버에 살아있는 실제 네트워크 연결"이라 다른 서버로 복사·이동이 불가능합니다. 그래서 각 서버 메모리에만 두고, 대신 **방송으로 알림만 옮깁니다.**

**Q. Redis가 죽으면요?**
- 실시간 방송은 잠깐 멈추지만, 비즈니스 로직(지원/승인 등)과 DB는 멀쩡합니다. 사용자가 새로고침하면 알림 목록 API로 확인 가능하고, Redis가 살아나면 재연결 시 보관함에서 복구됩니다.

**Q. `@class`니 직렬화니 하는 건 뭔가요? (RedisConfig)**
- 객체(봉투)를 Redis에 넣을 땐 글자(JSON)로 풀어서 저장하고, 꺼낼 땐 다시 객체로 조립해야 합니다. 그런데 "이게 원래 무슨 클래스였는지" 표시(`@class`)를 안 남기면, 꺼낼 때 그냥 `Map`(이름표 없는 상자)으로 나와서 조립이 안 됩니다. **예전 버그의 핵심이 이거였고**, 지금은 `@class` 표시를 남기도록 고쳤습니다. (자세한 건 옆 plan 문서 5.1)

---

## 7. 전체를 한 장으로

```
                          ┌─────────────────────── Redis ───────────────────────┐
                          │   ① 알림 사본 보관함(2h)      ② 방송 채널 sse:events   │
                          └──────▲──────────────────────────▲────────┬───────────┘
                                 │ 저장/조회                  │ 방송   │ 모두 수신
   [구독]                        │                           │        │
   브라우저 ──/subscribe──▶ Controller ─▶ SubscribeService ──┘        │
      ▲                                    (전화선 설치, 재연결 복구)   │
      │ 전화선(메모리)                                                  │
      │                                                                ▼
      └──────────────── SseEventSubscriber ◀───── (내 서버에 전화선 있으면 전달)
                              ▲
   [발송]                     │ 방송 쏨
   DB커밋 ─▶ Listener ─▶ SSENotificationService ─▶ SseEventPublisher ─┘
                         (봉투 만들고 사본 저장)
```

이 그림 하나가 머리에 들어오면, 나머지 코드는 "이 그림의 어느 부분인지"만 찾으면 됩니다.