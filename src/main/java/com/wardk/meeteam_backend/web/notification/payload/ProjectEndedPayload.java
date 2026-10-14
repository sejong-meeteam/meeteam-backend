package com.wardk.meeteam_backend.web.notification.payload;

import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@AllArgsConstructor
@Data
@Builder
public class ProjectEndedPayload implements Payload {

    private Long projectId;
    private Long memberId;
    private String projectName;
    private LocalDate occurredAt;

    public static Payload create(NotificationContext context) {
        return ProjectEndedPayload.builder()
                .projectId(context.getProjectId())
                .memberId(context.getReceiverId())
                .projectName(context.getProjectName())
                .occurredAt(context.getOccurredAt())
                .build();
    }
}
