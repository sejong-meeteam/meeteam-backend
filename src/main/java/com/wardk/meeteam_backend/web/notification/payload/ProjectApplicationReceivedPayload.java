package com.wardk.meeteam_backend.web.notification.payload;

import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;


@AllArgsConstructor
@Data
@Builder
public class ProjectApplicationReceivedPayload implements Payload {

    private Long applicationId;
    private Long projectId;
    private Long receiverId;
    private Long applicantId;
    private String applicantName;
    private String projectName;
    private LocalDate date;

    public static Payload create(NotificationContext context) {
        return ProjectApplicationReceivedPayload.builder()
                .applicationId(context.getApplicationId())
                .projectId(context.getProjectId())
                .receiverId(context.getReceiverId())
                .applicantId(context.getActorId())
                .applicantName(context.getActorName())
                .projectName(context.getProjectName())
                .date(context.getOccurredAt())
                .build();
    }
}
