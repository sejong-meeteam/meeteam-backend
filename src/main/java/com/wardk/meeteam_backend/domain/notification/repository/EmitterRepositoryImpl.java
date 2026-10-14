package com.wardk.meeteam_backend.domain.notification.repository;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * SSE Emitter와 이벤트 캐시를 관리하는 Repository 구현체.
 *
 * <h2>저장소 구조</h2>
 * <pre>
 * 1. Emitter 저장소 (In-Memory)
 *    - ConcurrentHashMap으로 관리
 *    - 서버 메모리에 저장되므로 서버 재시작 시 손실됨
 *    - 멀티 인스턴스 환경에서는 각 서버가 자신의 연결만 관리
 *
 * 2. 이벤트 캐시 저장소 (Redis)
 *    - 재연결 시 놓친 이벤트를 재전송하기 위한 캐시
 *    - ZSET + String 조합으로 저장
 * </pre>
 *
 * <h2>Redis 데이터 구조</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ ZSET: eventCache:{memberId}                                    │
 * │ - 역할: 이벤트 ID 목록을 시간순으로 정렬하여 저장 (인덱스)         │
 * │ - TTL: 2시간 (이벤트 데이터와 동일)                              │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ member (값)              │ score (점수 = 정렬 기준)              │
 * │ "5_1708934400000"        │ 1708934400000 (timestamp)            │
 * │ "5_1708934401000"        │ 1708934401000                        │
 * │ "5_1708934402000"        │ 1708934402000                        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ String: eventCache:evt:{eventId}                               │
 * │ - 역할: 실제 이벤트 데이터 저장                                  │
 * │ - TTL: 2시간                                                    │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ 예: eventCache:evt:5_1708934400000                              │
 * │ 값: SseEnvelope 객체 (자동 직렬화됨)                             │
 * │     {"type":"PROJECT_APPLY", "data":{...}, "createdAt":...}    │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>왜 ZSET과 String을 분리했나?</h2>
 * <pre>
 * - ZSET만 사용하면: 큰 객체를 저장하기 어렵고, 범위 검색 시 전체 데이터를 가져와야 함
 * - String만 사용하면: 시간순 정렬이 어렵고, "이 시간 이후의 이벤트"를 효율적으로 찾을 수 없음
 *
 * 분리함으로써:
 * - ZSET: 가벼운 인덱스 역할 (eventId와 timestamp만 저장)
 * - String: 실제 무거운 데이터 저장
 * - rangeByScore로 O(log N) 시간에 범위 검색 가능
 * </pre>
 */
@Repository
@RequiredArgsConstructor
public class EmitterRepositoryImpl implements EmitterRepository {

    // ==================== 상수 ====================
    // TODO: 리팩토링 - 상수를 별도 클래스나 @Value로 분리 권장
    /**
     * 이벤트 캐시 TTL (ZSET 인덱스 + 이벤트 데이터 모두 동일하게 적용)
     *
     * 2시간으로 설정한 이유:
     * - SSE 타임아웃(1시간) + 네트워크 장애 여유(1시간)
     * - 대부분의 네트워크 장애는 2시간 내 복구됨
     * - 2시간 넘게 오프라인이면 사용자가 새로고침할 가능성 높음
     * - Redis 메모리 효율적 사용
     */
    private static final Duration EVENT_CACHE_TTL = Duration.ofHours(2);
    private static final String EVENT_CACHE_PREFIX = "eventCache:";
    private static final String EVENT_DATA_PREFIX = "eventCache:evt:";

    /**
     * 이벤트 시퀀스 키 prefix.
     * TTL은 이벤트 캐시(2h)보다 충분히 길게 잡아, 캐시가 살아있는 동안 시퀀스가 리셋되어
     * 과거 score와 충돌하는 일이 없도록 한다.
     */
    private static final String EVENT_SEQ_PREFIX = "eventSeq:";
    private static final Duration EVENT_SEQ_TTL = Duration.ofDays(7);

    // ==================== 저장소 ====================

    /**
     * SSE Emitter 저장소 (In-Memory).
     *
     * Key: "{memberId}_{timestamp}" (예: "5_1708934400000")
     * Value: SseEmitter 객체
     *
     * 같은 사용자가 여러 탭/기기에서 접속하면 여러 Emitter가 저장됨.
     * 예: 사용자 5번이 3개의 탭을 열면:
     *   - "5_1708934400000" → Emitter1
     *   - "5_1708934401000" → Emitter2
     *   - "5_1708934402000" → Emitter3
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Redis String 전용 템플릿.
     * ZSET의 member와 score는 문자열/숫자이므로 StringRedisTemplate 사용.
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Redis Object 전용 템플릿.
     * SseEnvelope 같은 객체를 자동으로 직렬화/역직렬화.
     * (설정에 따라 JSON 또는 바이너리로 저장됨)
     */
    private final RedisTemplate<String, Object> redisObjectTemplate;

    // ==================== Emitter 관리 ====================

    /**
     * 새로운 SSE Emitter 저장.
     *
     * @param emitterId "{memberId}_{timestamp}" 형식
     * @param sseEmitter 저장할 Emitter 객체
     */
    @Override
    public SseEmitter save(String emitterId, SseEmitter sseEmitter) {
        emitters.put(emitterId, sseEmitter);
        return sseEmitter;
    }

    /**
     * 특정 사용자의 모든 활성 Emitter 조회.
     *
     * 사용 예: 알림 전송 시 해당 사용자의 모든 탭/기기에 브로드캐스트
     *
     * @param memberIdPrefix 사용자 ID (예: "5")
     * @return 해당 사용자의 모든 Emitter (예: {"5_xxx": emitter1, "5_yyy": emitter2})
     */
    @Override
    public Map<String, SseEmitter> findAllEmitterStartWithByMemberId(String memberIdPrefix) {
        Map<String, SseEmitter> result = new ConcurrentHashMap<>();
        emitters.forEach((key, emitter) -> {
            // key가 "5_"로 시작하면 사용자 5번의 Emitter
            if (key.startsWith(memberIdPrefix + "_")) {
                result.put(key, emitter);
            }
        });
        return result;
    }

    /**
     * 수신자별 전역 단조 증가 이벤트 시퀀스 발급 (Redis INCR).
     *
     * <pre>
     * - 원자적 연산이라 동일 밀리초에 여러 알림이 발생해도 시퀀스가 겹치지 않는다.
     * - Redis 단일 시계가 순서를 정의하므로, 발행 인스턴스가 달라도(clock skew) 순서가 보장된다.
     * - 반환값은 ZSET score(정렬 기준) 및 eventId 접미사로 사용된다.
     * </pre>
     */
    @Override
    public long nextSequence(Long memberId) {
        String key = EVENT_SEQ_PREFIX + memberId;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, EVENT_SEQ_TTL);
        // increment는 정상 상황에서 null이 아니지만, 방어적으로 fallback 제공
        return seq != null ? seq : System.currentTimeMillis();
    }

    @Override
    public void forEachEmitter(BiConsumer<String, SseEmitter> action) {
        emitters.forEach(action);
    }

    @Override
    public void deleteById(String emitterId) {
        emitters.remove(emitterId);
    }

    @Override
    public void deleteAllEmitterStartWithId(String memberIdPrefix) {
        emitters.keySet().removeIf(key -> key.startsWith(memberIdPrefix + "_"));
    }

    // ==================== 이벤트 캐시 관리 (Redis) ====================

    /**
     * 이벤트를 Redis에 캐시.
     *
     * <pre>
     * 저장 과정:
     * 1. eventId 파싱: "5_1708934400000" → memberId=5, timestamp=1708934400000
     * 2. String에 이벤트 데이터 저장 (key: eventCache:evt:5_1708934400000)
     * 3. ZSET에 인덱스 추가 (key: eventCache:5, member: eventId, score: timestamp)
     *
     * 이렇게 저장하면 나중에:
     * - "특정 시간 이후의 모든 이벤트"를 ZSET rangeByScore로 빠르게 조회 가능
     * - 조회된 eventId로 실제 데이터를 String에서 가져옴
     * </pre>
     *
     * @param eventId "{memberId}_{timestamp}" 형식
     * @param event 저장할 이벤트 객체 (SseEnvelope)
     */
    @Override
    public void saveEventCache(String eventId, Object event) {
        // 1. eventId 파싱
        String[] parts = eventId.split("_", 2);
        if (parts.length != 2 || parts[0].isBlank()) {
            throw new CustomException(ErrorCode.INVALID_EVENT_ID);
        }

        String memberId = parts[0];
        long timestamp;
        try {
            timestamp = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_EVENT_ID);
        }

        // 2. Redis 키 생성
        String zsetKey = EVENT_CACHE_PREFIX + memberId;      // "eventCache:5"
        String dataKey = EVENT_DATA_PREFIX + eventId;        // "eventCache:evt:5_1708934400000"

        // 3. 이벤트 데이터 저장 (String 타입)
        // redisObjectTemplate이 자동으로 SseEnvelope → JSON/바이너리 직렬화
        redisObjectTemplate.opsForValue().set(dataKey, event, EVENT_CACHE_TTL);

        // 4. ZSET에 인덱스 추가
        // add(key, member, score) → 정렬된 집합에 추가
        // member: eventId (문자열)
        // score: timestamp (정렬 기준, 숫자가 작을수록 앞에 위치)
        stringRedisTemplate.opsForZSet().add(zsetKey, eventId, timestamp);

        // 5. ZSET TTL 설정 (이벤트 데이터와 동일한 TTL 적용)
        stringRedisTemplate.expire(zsetKey, EVENT_CACHE_TTL);
    }

    /**
     * 특정 사용자의 모든 캐시된 이벤트 조회.
     *
     * <pre>
     * 조회 과정:
     * 1. ZSET에서 모든 eventId 조회 (시간순 정렬됨)
     * 2. 각 eventId로 String에서 실제 데이터 조회
     * </pre>
     *
     * @param memberIdPrefix 사용자 ID
     * @return eventId → 이벤트 데이터 맵 (시간순 정렬)
     */
    @Override
    public Map<String, Object> findAllEventCacheStartWithByMemberId(String memberIdPrefix) {
        String zsetKey = EVENT_CACHE_PREFIX + memberIdPrefix;

        // ZSET에서 모든 member 조회 (인덱스 0부터 끝(-1)까지)
        // rangeWithScores: member와 score를 함께 가져옴
        Set<TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().rangeWithScores(zsetKey, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }

        // LinkedHashMap: 삽입 순서 유지 (ZSET이 이미 정렬되어 있으므로)
        Map<String, Object> result = new LinkedHashMap<>();
        for (TypedTuple<String> tuple : tuples) {
            String eventId = tuple.getValue();  // ZSET member = eventId
            if (eventId == null) continue;

            // String에서 실제 이벤트 데이터 조회
            String dataKey = EVENT_DATA_PREFIX + eventId;
            Object payload = redisObjectTemplate.opsForValue().get(dataKey);

            // 데이터가 먼저 만료되었을 수 있음 (TTL 불일치 문제)
            if (payload != null) {
                result.put(eventId, payload);
            }
        }
        return result;
    }

    /**
     * 특정 시간 이후의 이벤트만 조회 (재연결 시 사용).
     *
     * <pre>
     * 사용 시나리오:
     * 1. 클라이언트가 연결 끊김 (마지막으로 받은 이벤트 ID: "5_1708934400000")
     * 2. 재연결 시 Last-Event-ID 헤더로 "5_1708934400000" 전송
     * 3. 서버는 timestamp 1708934400000 이후의 이벤트만 재전송
     *
     * ZSET rangeByScore 사용:
     * - 시간복잡도: O(log N + M) (N: 전체 개수, M: 결과 개수)
     * - 일반 배열 순회보다 훨씬 효율적
     * </pre>
     *
     * @param memberId 사용자 ID
     * @param afterTs 이 timestamp 이후의 이벤트만 조회
     * @return eventId → 이벤트 데이터 맵 (시간순 정렬)
     */
    @Override
    public Map<String, Object> findEventCacheAfterByMemberId(String memberId, long afterTs) {
        String zsetKey = EVENT_CACHE_PREFIX + memberId;

        // ZSET에서 score가 (afterTs, +∞) 범위인 member 조회
        // afterTs + 1: "이후"이므로 해당 시간은 제외 (strictly greater than)
        Set<TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .rangeByScoreWithScores(zsetKey, afterTs + 1, Double.POSITIVE_INFINITY);

        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (TypedTuple<String> tuple : tuples) {
            String eventId = tuple.getValue();
            if (eventId == null) continue;

            String dataKey = EVENT_DATA_PREFIX + eventId;
            Object payload = redisObjectTemplate.opsForValue().get(dataKey);

            if (payload != null) {
                result.put(eventId, payload);
            }
        }
        return result;
    }

    /**
     * 특정 사용자의 모든 이벤트 캐시 삭제.
     *
     * <pre>
     * 삭제 과정:
     * 1. ZSET에서 모든 eventId 조회
     * 2. 각 eventId에 해당하는 String 데이터 삭제
     * 3. ZSET 자체 삭제
     * </pre>
     */
    @Override
    public void deleteAllEventCacheStartWithId(String memberIdPrefix) {
        String zsetKey = EVENT_CACHE_PREFIX + memberIdPrefix;

        // ZSET의 모든 member(eventId) 조회
        Set<String> eventIds = stringRedisTemplate.opsForZSet().range(zsetKey, 0, -1);

        if (eventIds != null) {
            // 각 이벤트 데이터 삭제
            for (String eventId : eventIds) {
                stringRedisTemplate.delete(EVENT_DATA_PREFIX + eventId);
            }
        }

        // ZSET 삭제
        stringRedisTemplate.delete(zsetKey);
    }
}