package com.wardk.meeteam_backend.web.notification.strategy;

import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import com.wardk.meeteam_backend.web.notification.payload.ProjectApplicationApprovedPayload;
import com.wardk.meeteam_backend.web.notification.payload.ProjectApplicationRejectedPayload;
import org.springframework.stereotype.Component;

@Component
public class ProjectApplicationRejectedPayloadStrategy implements NotificationPayloadStrategy{
    @Override
    public NotificationType getType() {
        return NotificationType.PROJECT_REJECT;
    }

    @Override
    public Payload create(NotificationContext context) {
        return ProjectApplicationRejectedPayload.create(context);
    }
}
