package com.wardk.meeteam_backend.domain.notification;

import com.wardk.meeteam_backend.domain.notification.service.SSENotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final SSENotificationService notificationService;


    /**
     * 트랜잭션 커밋 이후 발행된 NotificationEvent를 비동기로 수신하여 처리하는 메서드입니다.
     *
     * @param e 알림 이벤트 객체 (수신자 ID, 발신자 ID, 프로젝트 ID, 알림 유형 포함)
     *
     * NotificationEvent 객체를 SSENotificationService에 넘겨서
     * 해당 서비스의 트랜잭션 내에서 엔티티 조회가 이루어지도록 합니다.
     * 이렇게 하면 @Async로 인한 영속성 컨텍스트 분리 문제를 해결할 수 있습니다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NotificationEvent e) {
        // 엔티티 조회를 SSENotificationService로 위임
        notificationService.notify(e);
    }


    // 프로젝트 삭제시
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ProjectEndEvent e) {
        notificationService.notify(e);
    }
}
