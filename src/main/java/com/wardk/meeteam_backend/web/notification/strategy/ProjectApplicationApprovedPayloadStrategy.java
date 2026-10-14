package com.wardk.meeteam_backend.web.notification.strategy;

import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import com.wardk.meeteam_backend.web.notification.payload.ProjectApplicationApprovedPayload;
import org.springframework.stereotype.Component;

@Component
public class ProjectApplicationApprovedPayloadStrategy implements NotificationPayloadStrategy{
    @Override
    public NotificationType getType() {
        return NotificationType.PROJECT_APPROVE;
    }

    @Override
    public Payload create(NotificationContext context) {
        return ProjectApplicationApprovedPayload.create(context);
    }
}
