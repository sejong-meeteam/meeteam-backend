package com.wardk.meeteam_backend.web.application.dto.response;

import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 내가 지원한 프로젝트 응답 DTO.
 */
@Schema(description = "내가 지원한 프로젝트 응답")
public record AppliedProjectResponse(
        @Schema(description = "지원서 ID")
        Long applicationId,

        @Schema(description = "프로젝트 ID")
        Long projectId,

        @Schema(description = "프로젝트명")
        String projectName,

        @Schema(description = "프로젝트 이미지 URL")
        String projectImageUrl,

        @Schema(description = "지원 포지션 ID")
        Long jobPositionId,

        @Schema(description = "지원 포지션명")
        String jobPositionName,

        @Schema(description = "지원 상태", example = "PENDING | ACCEPTED | REJECTED")
        String status,

        @Schema(description = "지원 상태 한글명", example = "대기중 | 승인됨 | 거절됨")
        String statusDisplayName,

        @Schema(description = "지원 일시")
        LocalDateTime appliedAt
) {
    public static AppliedProjectResponse from(ProjectApplication application) {
        return new AppliedProjectResponse(
                application.getId(),
                application.getProject().getId(),
                application.getProject().getName(),
                application.getProject().getImageUrl(),
                application.getJobPosition().getId(),
                application.getJobPosition().getName(),
                application.getStatus().name(),
                application.getStatus().getDisplayName(),
                application.getCreatedAt()
        );
    }
}
