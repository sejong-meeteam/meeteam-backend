package com.wardk.meeteam_backend.web.auth.dto.register;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 회원가입 시 직군/직무/기술스택 선택 정보를 담는 요청 DTO.
 * 프로젝트 등록의 ProjectRecruitRequest와 유사한 구조로,
 * JobField -> JobPosition 선택 후 해당 포지션에 맞는 기술스택을 선택합니다.
 */
public record MemberJobPositionRequest(
        @NotNull(message = "직군 코드는 필수입니다.")
        @Schema(description = "직군 코드", example = "BACKEND")
        JobFieldCode jobFieldCode,

        @NotNull(message = "직무 포지션 코드는 필수입니다.")
        @Schema(description = "직무 포지션 코드", example = "JAVA_SPRING")
        JobPositionCode jobPositionCode,

        @ArraySchema(
                schema = @Schema(implementation = TechStackOrderRequest.class),
                arraySchema = @Schema(description = "선택한 기술스택 목록 (해당 직군의 기술스택 ID)")
        )
        @Valid
        List<TechStackOrderRequest> techStacks
) {
    public MemberJobPositionRequest {
        if (techStacks == null) {
            techStacks = List.of();
        }
    }
}