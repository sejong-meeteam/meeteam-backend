package com.wardk.meeteam_backend.web.application.dto.response;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로젝트 지원 승인/거절 응답 DTO.
 */
@Schema(description = "프로젝트 지원 승인/거절 응답")
public record ApplicationDecisionResponse(
        @Schema(description = "지원서 ID")
        Long applicationId,

        @Schema(description = "프로젝트 ID")
        Long projectId,

        @Schema(description = "지원자 ID (승인 시에만 포함)")
        Long applicantId,

        @Schema(description = "처리 결과")
        ApplicationStatus decision
) {
    public static ApplicationDecisionResponse rejectResponseDto(Long applicationId, ApplicationStatus decision) {
        return new ApplicationDecisionResponse(
                applicationId,
                null,
                null,
                decision
        );
    }

    public static ApplicationDecisionResponse acceptResponseDto(Long applicationId, Long projectId, Long applicantId, ApplicationStatus decision) {
        return new ApplicationDecisionResponse(
                applicationId,
                projectId,
                applicantId,
                decision
        );
    }
}
