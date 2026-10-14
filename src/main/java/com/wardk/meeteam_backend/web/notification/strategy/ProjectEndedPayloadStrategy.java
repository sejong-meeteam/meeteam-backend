package com.wardk.meeteam_backend.web.notification.strategy;

import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import com.wardk.meeteam_backend.web.notification.payload.ProjectEndedPayload;
import org.springframework.stereotype.Component;

/**
 * PROJECT_ENDED 알림 Payload 생성 전략.
 * 프로젝트 종료/삭제 시 팀원들에게 알림을 보낼 때 사용.
 */
@Component
public class ProjectEndedPayloadStrategy implements NotificationPayloadStrategy {

    @Override
    public NotificationType getType() {
        return NotificationType.PROJECT_END;
    }

    @Override
    public Payload create(NotificationContext context) {
        return ProjectEndedPayload.create(context);
    }
}
