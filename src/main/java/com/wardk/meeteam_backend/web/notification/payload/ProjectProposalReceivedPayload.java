package com.wardk.meeteam_backend.web.notification.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class ProjectProposalReceivedPayload implements Payload {

    private Long projectId;
    private String projectName;
    private Long proposerId;
    private String proposerName;
    private String jobFieldName;
    private String jobPositionName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String proposalMessage;

    public static Payload create(NotificationContext context) {
        return ProjectProposalReceivedPayload.builder()
                .projectId(context.getProjectId())
                .projectName(context.getProjectName())
                .proposerId(context.getActorId())
                .proposerName(context.getActorName())
                .jobFieldName(context.getJobFieldName())
                .jobPositionName(context.getJobPositionName())
                .proposalMessage(context.getProposalMessage())
                .build();
    }
}
