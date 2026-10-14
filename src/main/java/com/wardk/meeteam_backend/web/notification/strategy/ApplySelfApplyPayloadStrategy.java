package com.wardk.meeteam_backend.web.notification.strategy;

import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import com.wardk.meeteam_backend.web.notification.payload.ProjectApplicationSubmittedPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ApplySelfApplyPayloadStrategy implements NotificationPayloadStrategy {

    @Override
    public NotificationType getType() {
        return NotificationType.PROJECT_MY_APPLY;
    }

    @Override
    public Payload create(NotificationContext context) {
        return ProjectApplicationSubmittedPayload.create(context);
    }
}
