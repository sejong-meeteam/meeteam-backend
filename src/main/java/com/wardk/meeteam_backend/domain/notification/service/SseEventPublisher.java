package com.wardk.meeteam_backend.domain.notification.service;

import com.wardk.meeteam_backend.web.notification.SsePublishMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SSE 알림을 Redis Pub/Sub 채널로 발행하는 컴포넌트.
 *
 * <p>알림 발행 인스턴스는 emitter에 직접 쓰지 않고 이 채널에 publish 한다. 모든 인스턴스가
 * 채널을 구독하고 있으므로, 해당 수신자의 SSE 연결을 들고 있는 인스턴스가 메시지를 받아 전송한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventPublisher {

    /** SSE 전파 채널명. {@code RedisPubSubConfig}의 토픽과 일치해야 한다. */
    public static final String SSE_CHANNEL = "sse:events";

    private final RedisTemplate<String, Object> redisObjectTemplate;

    public void publish(SsePublishMessage message) {
        try {
            redisObjectTemplate.convertAndSend(SSE_CHANNEL, message);
        } catch (Exception e) {
            // 발행 실패가 비즈니스 로직/트랜잭션에 영향을 주지 않도록 격리한다.
            // 사용자는 재연결 시 Redis 이벤트 캐시 replay로 누락분을 복구한다.
            log.error("[알림] SSE publish 실패 - receiver: {}, eventId: {}, error: {}",
                    message.getReceiverId(), message.getEventId(), e.getMessage());
        }
    }
}