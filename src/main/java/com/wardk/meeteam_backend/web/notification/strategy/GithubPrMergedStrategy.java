package com.wardk.meeteam_backend.web.notification.strategy;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.*;
import org.springframework.stereotype.Component;
@Component public class GithubPrMergedStrategy implements NotificationPayloadStrategy {
 public NotificationType getType(){return NotificationType.GITHUB_PR_MERGED;}
 public Payload create(NotificationContext context){return ProjectEndedPayload.create(context);}
}
