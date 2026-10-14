package com.wardk.meeteam_backend.web.application.dto.response;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로젝트 지원 응답 DTO.
 */
@Schema(description = "프로젝트 지원 응답")
public record ApplicationResponse(
        @Schema(description = "지원서 ID")
        Long applicationId,

        @Schema(description = "프로젝트 ID")
        Long projectId,

        @Schema(description = "지원자 ID")
        Long applicantId,

        @Schema(description = "지원 상태")
        ApplicationStatus status
) {
    public static ApplicationResponse from(ProjectApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getProject().getId(),
                application.getApplicant().getId(),
                application.getStatus()
        );
    }
}
