package com.wardk.meeteam_backend.web.notification.strategy;

import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import com.wardk.meeteam_backend.web.notification.payload.ProjectProposalReceivedPayload;
import org.springframework.stereotype.Component;

@Component
public class ProjectProposalReceivedPayloadStrategy implements NotificationPayloadStrategy {

    @Override
    public NotificationType getType() {
        return NotificationType.PROJECT_PROPOSAL_RECEIVED;
    }

    @Override
    public Payload create(NotificationContext context) {
        return ProjectProposalReceivedPayload.create(context);
    }
}
