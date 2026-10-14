package com.wardk.meeteam_backend.domain.notification.service;


import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.repository.EmitterRepository;
import com.wardk.meeteam_backend.domain.notification.repository.NotificationRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.notification.SseEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSubscribeService {

    private static final long DEFAULT_TIMEOUT = 60L * 60L * 1000L; // 1h

    private final NotificationRepository notificationRepository;
    private final EmitterRepository emitterRepository;
    private final MemberRepository memberRepository;
    private final SseEmitterSender emitterSender;

    // ====== 구독 ======
    // emitter 를 client 에게 반환 ( 서버로 부터 이벤트를 client 가 받을 수 있게 된다.)

    public SseEmitter subscribe(String email, String lastEventId) {

        // 소프트 삭제(deletedAt) 회원이 같은 이메일로 남아있을 수 있어(uk: email+deleted_at),
        // 활성 회원만 조회해야 NonUniqueResultException(2 results)이 발생하지 않는다. (로그인과 동일 기준)
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow( () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        Long memberId = member.getId();

        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT); // 1시간 타임아웃으로 SSE 생성

        // 저장 및 수명 관리
        String emitterId = makeEmitterId(memberId); // "{memberId}_{UUID}"
        emitterRepository.save(emitterId, emitter); // 메모리에 emitter 등록

        //즉시 실행이 아니라 미래에 발생할 이벤트에 대비한 예약 실행 (바로 실행되는게 아니라 미리 등록해 놓는것이다.)
        emitter.onCompletion(() -> emitterRepository.deleteById(emitterId)); // 정상 종료 시 정리
        emitter.onTimeout(() -> emitterRepository.deleteById(emitterId)); // 타임아웃 시 정리
        emitter.onError(e -> emitterRepository.deleteById(emitterId)); // 에러 시 정리

        // 1) 연결 확인용 더미 이벤트(503 방지)
        sendPing(emitterId, emitter);


        // ── 재연결: Last-Event-ID 이후의 미수신 이벤트 재전송 (ZSET score 범위 조회)
        if (hasText(lastEventId)) {
            long afterSeq = extractSeq(lastEventId);
            if (afterSeq != Long.MIN_VALUE) {
                Map<String, Object> cachedEvents =
                        emitterRepository.findEventCacheAfterByMemberId(String.valueOf(memberId), afterSeq); // Redis에서 순서대로 Map
                cachedEvents.forEach((eid, payload) -> sendReplay(emitterId, emitter, eid, payload)); // 이벤트명(name) 포함 재전송
            } else {
                log.warn("Skip replay due to non-numeric Last-Event-ID: {}", lastEventId);
            }
        }

        return emitter;
    }


    // emitterId 구조: "{memberId}_{UUID}". 순서가 필요 없으므로 충돌만 차단되면 된다.
    // prefix 매칭(findAllEmitterStartWithByMemberId)을 위해 "{memberId}_" 형식을 유지한다.
    private String makeEmitterId(Long memberId) {
        return memberId + "_" + UUID.randomUUID();
    }

    private void sendPing(String emitterId, SseEmitter emitter) {
        emitterSender.send(emitterId, emitter, SseEmitter.event()
                .name("PING")
                .data("connected"));
    }


    /**
     * eventId("{memberId}_{seq}")에서 seq(정렬용 단조 증가 값)를 추출한다.
     * 형식이 올바르지 않으면 {@link Long#MIN_VALUE}를 반환해 replay를 건너뛴다.
     */
    private long extractSeq(String eventId) {
        if (eventId == null || eventId.isBlank()) return Long.MIN_VALUE;
        int idx = eventId.lastIndexOf('_');
        String numeric = (idx >= 0 && idx + 1 < eventId.length())
                ? eventId.substring(idx + 1)
                : eventId;
        try {
            return Long.parseLong(numeric);
        } catch (NumberFormatException e) {
            log.warn("Invalid eventId format (suffix not numeric): {}", eventId);
            return Long.MIN_VALUE;
        }
    }


    /**
     * 캐시된 이벤트를 재전송한다.
     *
     * <p>역직렬화 결과가 기대한 타입이 아니거나 비어 있어도 해당 이벤트만 건너뛴다.
     * 단일 이벤트의 문제가 구독(연결) 전체를 깨뜨리지 않도록 방어적으로 처리한다.</p>
     */
    private void sendReplay(String emitterId, SseEmitter emitter, String eventId, Object data) {
        if (data instanceof SseEnvelope<?> env && env.getType() != null) {
            emitterSender.send(emitterId, emitter, eventId, env.getType().name(), data);
        } else {
            log.warn("Skip replay for event {} - unexpected cached type: {}",
                    eventId, data != null ? data.getClass().getName() : "null");
        }
    }

}
