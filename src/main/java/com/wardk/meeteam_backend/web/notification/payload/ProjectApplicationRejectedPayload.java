package com.wardk.meeteam_backend.web.notification.payload;


import com.wardk.meeteam_backend.web.notification.ApprovalResult;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
public class ProjectApplicationRejectedPayload implements Payload {

    private Long receiverId;
    private Long projectId;
    private ApprovalResult approvalResult;
    private LocalDate date;

    public static Payload create(NotificationContext context) {
        return ProjectApplicationRejectedPayload.builder()
                .receiverId(context.getReceiverId())
                .projectId(context.getProjectId())
                .approvalResult(ApprovalResult.REJECTED)
                .date(context.getOccurredAt())
                .build();
    }
}
