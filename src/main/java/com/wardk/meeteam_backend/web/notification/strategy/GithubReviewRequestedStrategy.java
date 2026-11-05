package com.wardk.meeteam_backend.web.notification.strategy;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.*;
import org.springframework.stereotype.Component;
@Component public class GithubReviewRequestedStrategy implements NotificationPayloadStrategy {
 public NotificationType getType(){return NotificationType.GITHUB_REVIEW_REQUESTED;}
 public Payload create(NotificationContext context){return ProjectEndedPayload.create(context);}
}
