package com.wardk.meeteam_backend.web.application.dto.request;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 프로젝트 지원 승인/거절 요청 DTO.
 */
@Schema(description = "프로젝트 지원 승인/거절 요청")
public record ApplicationDecisionRequest(
        @NotNull(message = "결정은 필수입니다.")
        @Schema(description = "지원 처리 결정 (ACCEPTED 또는 REJECTED)", example = "ACCEPTED")
        ApplicationStatus decision
) {
}
