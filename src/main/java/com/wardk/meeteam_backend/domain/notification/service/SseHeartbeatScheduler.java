package com.wardk.meeteam_backend.domain.notification.service;

import com.wardk.meeteam_backend.domain.notification.repository.EmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 활성 SSE 연결에 주기적으로 하트비트(주석 라인)를 보내 idle 연결 종료를 방지한다.
 *
 * <p>Nginx/ALB 등 프록시의 기본 idle 타임아웃(보통 60초)보다 짧은 주기로 전송한다.
 * SSE 주석({@code :} 라인)은 클라이언트의 onmessage를 트리거하지 않으면서 연결만 유지시킨다.
 * 전송 실패한 emitter는 {@link SseEmitterSender}가 즉시 정리하므로 dead emitter도 함께 청소된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final EmitterRepository emitterRepository;
    private final SseEmitterSender emitterSender;

    @Scheduled(fixedRate = 30_000) // 30초
    public void heartbeat() {
        emitterRepository.forEachEmitter((emitterId, emitter) -> {
            SseEmitter.SseEventBuilder ping = SseEmitter.event().comment("heartbeat");
            emitterSender.send(emitterId, emitter, ping);
        });
    }
}
