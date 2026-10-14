package com.wardk.meeteam_backend.domain.notification;

import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectEndEvent {

    private NotificationType type;

    private List<Long> projectMembersId;

    private Long projectId;

    private String projectName;

    private LocalDate occurredAt;


}
