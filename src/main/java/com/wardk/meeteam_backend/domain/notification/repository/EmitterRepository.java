package com.wardk.meeteam_backend.domain.notification.repository;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.function.BiConsumer;

public interface EmitterRepository {


    SseEmitter save(String emitterId, SseEmitter sseEmitter);

    void saveEventCache(String eventId, Object event);

    /**
     * 수신자별 전역 단조 증가 이벤트 시퀀스를 발급한다 (Redis INCR).
     * 동일 밀리초 충돌을 원천 차단하고, 멀티 인스턴스 환경에서 순서의 단일 소스 역할을 한다.
     */
    long nextSequence(Long memberId);

    /**
     * 현재 인스턴스 메모리에 보관된 모든 Emitter를 순회한다 (하트비트 등에 사용).
     */
    void forEachEmitter(BiConsumer<String, SseEmitter> action);

    Map<String, SseEmitter> findAllEmitterStartWithByMemberId(String memberIdPrefix);

    Map<String, Object> findAllEventCacheStartWithByMemberId(String memberIdPrefix);

    void deleteById(String emitterId);

    void deleteAllEmitterStartWithId(String memberIdPrefix);

    void deleteAllEventCacheStartWithId(String memberIdPrefix);


    Map<String, Object> findEventCacheAfterByMemberId(String memberId, long afterTs);
}

