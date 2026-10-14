package com.wardk.meeteam_backend.web.auth.dto.register;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 기술스택 선택 정보 요청 DTO.
 * 기술스택 ID와 표시 순서(displayOrder)를 함께 전달합니다.
 */
public record TechStackOrderRequest(
        @NotNull(message = "기술스택 ID는 필수입니다.")
        @Schema(description = "기술스택 ID (/api/jobs/options 응답에서 선택한 직군의 techStacks.id 값)", example = "30")
        Long id,

        @NotNull(message = "표시 순서는 필수입니다.")
        @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다.")
        @Schema(description = "표시 순서 (1부터 시작)", example = "1")
        Integer displayOrder
) {
}