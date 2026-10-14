package com.wardk.meeteam_backend.web.auth.dto.register;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "직무 포지션 요청")
public record JobPositionRequest(
        Long jobPositionId
) {
}
