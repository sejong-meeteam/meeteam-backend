package com.wardk.meeteam_backend.domain.notification.service;


import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.notification.ProjectEndEvent;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.repository.EmitterRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.notification.SseEnvelope;
import com.wardk.meeteam_backend.web.notification.SsePublishMessage;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.factory.NotificationPayloadFactory;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SSE 기반 실시간 알림 서비스.
 *
 * 트랜잭션 및 비동기 처리 전략:
 * - 이 서비스는 @Async + @TransactionalEventListener(AFTER_COMMIT)로 호출됨
 * - 호출 시점에 원본 트랜잭션은 이미 커밋된 상태
 * - 새로운 트랜잭션에서 엔티티 조회 및 알림 저장 수행
 * - SSE 전송은 트랜잭션과 무관하게 진행 (실패해도 DB는 커밋됨)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SSENotificationService {

    private final EmitterRepository emitterRepository;
    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final NotificationPayloadFactory payloadFactory;
    private final SseEventPublisher ssePublisher;

    /**
     * NotificationEvent 처리 - 단일 수신자 알림
     * 새로운 트랜잭션에서 실행되어 엔티티 조회 및 알림 저장이 가능함.
     */
    @Transactional
    public void notify(NotificationEvent event) {
        NotificationType type = event.getType();

        // 1. 필수 검증
        validateActorIfRequired(type, event.getActorId());

        // 2. 엔티티 조회
        Member receiver = findMemberOrThrow(event.getReceiverId(), "수신자");
        Project project = findProjectOrThrow(event.getProjectId());
        Member actor = findActorIfRequired(type, event.getActorId());

        // 3. Context 생성 (엔티티에서 필요한 데이터 추출)
        NotificationContext context = NotificationContext.of(event, project, actor);

        // 4. SSE 전송 (전송 실패가 저장 트랜잭션을 롤백시키지 않도록 예외 처리)
        // 주의: Notification 엔티티 저장은 메인 비즈니스 로직(ProjectApplicationService)에서 이미 수행됨
        try {
            Payload payload = payloadFactory.create(type, context);
            broadcastToReceiver(receiver.getId(), type, payload);
            log.info("[알림] 전송 완료 - type: {}, receiver: {}", type, receiver.getId());
        } catch (Exception e) {
            log.error("[알림] 전송 실패 (DB 저장은 완료됨) - type: {}, receiver: {}, error: {}", type, receiver.getId(), e.getMessage());
        }
    }

    /**
     * ProjectEndEvent 처리 - 다중 수신자 알림
     * 프로젝트 종료/삭제 시 모든 팀원에게 SSE 전송.
     * 주의: 알림 DB 저장은 서비스 레이어(ProjectServiceImpl)에서 이미 완료됨.
     */
    @Transactional(readOnly = true)
    public void notify(ProjectEndEvent event) {
        NotificationType type = event.getType();
        Long projectId = event.getProjectId();
        String projectName = event.getProjectName();

        for (Long memberId : event.getProjectMembersId()) {
            try {
                // Context 생성
                NotificationContext context = NotificationContext.forProjectEnded(
                        memberId, projectId, projectName
                );

                // SSE 전송
                Payload payload = payloadFactory.create(type, context);
                broadcastToReceiver(memberId, type, payload);
                log.info("[알림] PROJECT_END 전송 - receiver: {}, project: {}", memberId, projectId);

            } catch (Exception e) {
                log.error("[알림] PROJECT_END 전송 실패 (DB 저장은 완료됨) - receiver: {}, error: {}", memberId, e.getMessage());
            }
        }
    }



    private void validateActorIfRequired(NotificationType type, Long actorId) {
        if (type.requiresActor() && actorId == null) {
            throw new CustomException(ErrorCode.ACTOR_NOT_FOUND);
        }
    }

    private Member findMemberOrThrow(Long memberId, String role) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.error("[알림] {}를 찾을 수 없음: {}", role, memberId);
                    return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
                });
    }

    private Project findProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.error("[알림] 프로젝트를 찾을 수 없음: {}", projectId);
                    return new CustomException(ErrorCode.PROJECT_NOT_FOUND);
                });
    }

    private Member findActorIfRequired(NotificationType type, Long actorId) {
        if (actorId != null && type.requiresActor()) {
            return memberRepository.findById(actorId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ACTOR_NOT_FOUND));
        }
        return null;
    }

    private void broadcastToReceiver(Long receiverId, NotificationType type, Payload payload) {

        SseEnvelope<Object> envelope = SseEnvelope.builder()
                .type(type)
                .data(payload)
                .createdAt(LocalDateTime.now())
                .build();

        // 1) 재연결 복구용 캐시 저장 (Redis)
        String eventId = makeEventId(receiverId);
        emitterRepository.saveEventCache(eventId, envelope);

        // 2) 멀티 인스턴스 전파: 직접 emitter에 쓰지 않고 채널에 발행한다.
        //    수신자 연결을 보유한 인스턴스가 구독을 통해 받아 전송한다(발행 인스턴스 자신 포함).
        ssePublisher.publish(SsePublishMessage.builder()
                .receiverId(receiverId)
                .eventId(eventId)
                .envelope(envelope)
                .build());
    }

    /**
     * eventId = "{memberId}_{seq}". seq는 Redis INCR 기반 전역 단조 증가 값으로,
     * 동일 ms 충돌을 차단하고 멀티 인스턴스 순서의 단일 소스가 된다.
     */
    private String makeEventId(Long memberId) {
        return memberId + "_" + emitterRepository.nextSequence(memberId);
    }
}
