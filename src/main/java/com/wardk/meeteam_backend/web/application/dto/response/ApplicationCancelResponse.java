package com.wardk.meeteam_backend.web.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원 취소 응답 DTO.
 */
@Schema(description = "지원 취소 응답")
public record ApplicationCancelResponse(
        @Schema(description = "취소된 지원서 ID")
        Long applicationId,

        @Schema(description = "프로젝트 ID")
        Long projectId,

        @Schema(description = "프로젝트명")
        String projectName,

        @Schema(description = "취소된 상태", example = "CANCELLED")
        String status
) {
    public static ApplicationCancelResponse of(Long applicationId, Long projectId, String projectName) {
        return new ApplicationCancelResponse(applicationId, projectId, projectName, "CANCELLED");
    }
}