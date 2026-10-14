package com.wardk.meeteam_backend.web.projectmember.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectUpdateResponse {

    private Long projectId;

    public static ProjectUpdateResponse responseDto(Long projectId) {
        return ProjectUpdateResponse.builder()
                .projectId(projectId)
                .build();
    }
}
