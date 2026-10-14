package com.wardk.meeteam_backend.web.project.dto.request;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.project.service.dto.RecruitmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 프로젝트 모집 정보 요청 DTO.
 * JobField와 JobPosition은 ENUM 코드로 지정하여 타입 안전성을 보장합니다.
 */
public record ProjectRecruitRequest(
        @NotNull(message = "직군 코드는 필수입니다.")
        @Schema(description = "직군 코드", example = "BACKEND")
        JobFieldCode jobFieldCode,

        @NotNull(message = "직무 포지션 코드는 필수입니다.")
        @Schema(description = "직무 포지션 코드", example = "JAVA_SPRING")
        JobPositionCode jobPositionCode,

        @NotNull(message = "모집 인원은 필수입니다.")
        @Min(value = 1, message = "모집 인원은 최소 1명 이상이어야 합니다.")
        @Schema(description = "모집 인원", example = "2")
        Integer recruitmentCount,

        @NotEmpty(message = "모집 포지션별 기술 스택은 최소 1개 이상이어야 합니다.")
        @Schema(description = "기술 스택 ID 목록 (/api/jobs/options 응답에서 선택한 직군의 techStacks.id 값)", example = "[30, 31, 38]")
        List<Long> techStackIds
) {
    /**
     * Request DTO를 도메인 Command로 변환합니다.
     */
    public RecruitmentCommand toCommand() {
        return new RecruitmentCommand(
                this.jobFieldCode,
                this.jobPositionCode,
                this.recruitmentCount,
                this.techStackIds
        );
    }
}
