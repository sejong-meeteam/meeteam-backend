package com.wardk.meeteam_backend.domain.notification.service;

import com.wardk.meeteam_backend.domain.notification.repository.EmitterRepository;
import com.wardk.meeteam_backend.web.notification.SseEnvelope;
import com.wardk.meeteam_backend.web.notification.SsePublishMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Redis Pub/Sub {@code sse:events} 채널을 구독하여, 이 인스턴스가 보유한 SSE 연결로 알림을 전송한다.
 *
 * <p>모든 인스턴스에서 동작하지만, 메시지의 수신자가 이 인스턴스 메모리에 연결을 가지고 있을 때만
 * 실제 전송한다(없으면 즉시 반환). 발행 인스턴스 자신도 구독자이므로, 자기 연결은 이 경로로 전송된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventSubscriber implements MessageListener {

    private final EmitterRepository emitterRepository;
    private final SseEmitterSender emitterSender;
    private final GenericJackson2JsonRedisSerializer redisValueSerializer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        SsePublishMessage payload;
        try {
            payload = (SsePublishMessage) redisValueSerializer.deserialize(message.getBody());
        } catch (Exception e) {
            log.error("[알림] SSE 메시지 역직렬화 실패 - error: {}", e.getMessage());
            return;
        }
        if (payload == null) {
            return;
        }

        Map<String, SseEmitter> emitters =
                emitterRepository.findAllEmitterStartWithByMemberId(String.valueOf(payload.getReceiverId()));
        if (emitters.isEmpty()) {
            // 이 인스턴스에는 해당 수신자의 연결이 없음 → 다른 인스턴스가 처리
            return;
        }

        SseEnvelope<Object> envelope = payload.getEnvelope();
        String eventName = envelope.getType() != null ? envelope.getType().name() : "MESSAGE";

        emitters.forEach((emitterId, emitter) ->
                emitterSender.send(emitterId, emitter, payload.getEventId(), eventName, envelope));
    }
}