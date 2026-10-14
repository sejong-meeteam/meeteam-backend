package com.wardk.meeteam_backend.web.project.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectDeleteResponse {

    private Long projectId;
    private String projectName;

    public static ProjectDeleteResponse responseDto(Long projectId, String projectName) {
        return ProjectDeleteResponse.builder()
                .projectId(projectId)
                .projectName(projectName)
                .build();
    }
}
