package com.wardk.meeteam_backend.web.notification.strategy;

import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import com.wardk.meeteam_backend.web.notification.payload.ProjectApplicationReceivedPayload;
import org.springframework.stereotype.Component;


/**
 * PROJECT_APPLY 알림 Payload 생성 전략.
 * 팀장에게 "누군가 프로젝트에 지원했습니다" 알림을 보낼 때 사용.
 */
@Component
public class ProjectApplyPayloadStrategy implements NotificationPayloadStrategy {

    @Override
    public NotificationType getType() {
        return NotificationType.PROJECT_APPLY;
    }

    @Override
    public Payload create(NotificationContext context) {
        return ProjectApplicationReceivedPayload.create(context);
    }
}
