package com.wardk.meeteam_backend.web.notification.strategy;

import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;

public interface NotificationPayloadStrategy {
    NotificationType getType();
    Payload create(NotificationContext context);
}
