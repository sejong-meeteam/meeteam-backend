package com.wardk.meeteam_backend.domain.notification.service;

import com.wardk.meeteam_backend.domain.notification.repository.EmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SseEmitter 전송과 실패 시 정리를 한 곳에서 처리하는 공통 유틸.
 *
 * <p>기존 코드는 {@code catch (IOException)}만 처리해, 이미 완료된 emitter에 전송할 때 발생하는
 * {@link IllegalStateException}을 놓쳐 dead emitter가 맵에 남는 문제가 있었다. 여기서는
 * 모든 예외를 잡아 전송을 격리하고, 실패한 emitter는 즉시 완료·정리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterSender {

    private final EmitterRepository emitterRepository;

    /**
     * id/name이 있는 정식 이벤트를 전송한다. 실패 시 emitter를 정리한다.
     *
     * @return 전송 성공 여부
     */
    public boolean send(String emitterId, SseEmitter emitter, String eventId, String name, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(eventId)
                    .name(name)
                    .data(data));
            return true;
        } catch (Exception e) {
            cleanUp(emitterId, emitter, e);
            return false;
        }
    }

    /**
     * 하트비트/핑 등 정리만 보장하면 되는 경량 전송.
     */
    public boolean send(String emitterId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (Exception e) {
            cleanUp(emitterId, emitter, e);
            return false;
        }
    }

    private void cleanUp(String emitterId, SseEmitter emitter, Exception e) {
        log.warn("[알림] SSE 전송 실패 - emitter: {}, error: {}", emitterId, e.getMessage());
        emitterRepository.deleteById(emitterId);
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // 이미 완료된 emitter면 completeWithError가 예외를 던질 수 있으나 무시한다.
        }
    }
}