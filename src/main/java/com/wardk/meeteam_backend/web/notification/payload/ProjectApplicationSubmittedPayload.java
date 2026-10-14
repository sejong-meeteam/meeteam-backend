package com.wardk.meeteam_backend.web.notification.payload;


import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
public class ProjectApplicationSubmittedPayload implements Payload {

    private Long receiverId;
    private String projectName;
    private LocalDate localDate;

    public static Payload create(NotificationContext context) {
        return ProjectApplicationSubmittedPayload.builder()
                .receiverId(context.getReceiverId())
                .projectName(context.getProjectName())
                .localDate(context.getOccurredAt())
                .build();
    }
}
