package com.wardk.meeteam_backend.web.notification.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import lombok.Data;

@Data
public class NotificationPayload implements Payload {

    private Long projectId;
    private String projectName;
    private Long receiverId;
    private String receiverName;

    // PROJECT_PROPOSAL_RECEIVED 전용. 다른 알림 타입에서는 응답에 포함하지 않는다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long proposerId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String proposerName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String jobFieldName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String jobPositionName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String proposalMessage;

    public NotificationPayload(Project project, Member receiver, ProjectProposal proposal) {
        this.projectId = project.getId();
        this.projectName = project.getName();
        this.receiverId = receiver.getId();
        this.receiverName = receiver.getRealName();
        if (proposal == null) return;
        this.proposerId = proposal.getProposer().getId();
        this.proposerName = proposal.getProposer().getPublicName();
        this.jobFieldName = proposal.getJobPosition().getJobField().getName();
        this.jobPositionName = proposal.getJobPosition().getName();
        this.proposalMessage = proposal.getMessage().isBlank() ? null : proposal.getMessage();
    }
}
