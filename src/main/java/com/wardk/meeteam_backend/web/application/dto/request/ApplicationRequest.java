package com.wardk.meeteam_backend.web.application.dto.request;

import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 프로젝트 지원 요청 DTO.
 */
@Schema(description = "프로젝트 지원 요청")
public record ApplicationRequest(
        @NotNull
        @Schema(description = "지원할 포지션 코드", example = "WEB_FRONTEND")
        JobPositionCode jobPositionCode,

        @NotBlank
        @Schema(description = "지원 사유 및 자기소개", example = "이 프로젝트에 참여하고 싶습니다. 저는 3년차 프론트엔드 개발자입니다.")
        String motivation
) {
}
