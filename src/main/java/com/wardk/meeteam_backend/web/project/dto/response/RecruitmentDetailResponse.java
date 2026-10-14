package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 모집 분야 상세 응답 DTO.
 */
@Schema(description = "모집 분야 정보")
public record RecruitmentDetailResponse(
        // 직군 정보
        @Schema(description = "직군 코드", example = "BACKEND")
        String jobFieldCode,

        @Schema(description = "직군 이름", example = "백엔드")
        String jobFieldName,

        @Schema(description = "직무 이름", example = "Java/Spring")
        String jobPositionName,

        // 모집 현황
        @Schema(description = "모집 인원")
        int recruitmentCount,

        @Schema(description = "현재 인원")
        int currentCount,

        @Schema(description = "모집 마감 여부")
        boolean isClosed,

        // 필요 기술스택
        @Schema(description = "필요 기술스택 목록")
        List<String> techStacks
) {
    public static RecruitmentDetailResponse from(RecruitmentState recruitment) {
        JobPosition position = recruitment.getJobPosition();
        JobField field = recruitment.getJobField();

        List<String> techStacks = recruitment.getRecruitmentTechStacks().stream()
                .map(rts -> rts.getTechStack().getName())
                .toList();

        return new RecruitmentDetailResponse(
                field.getCode().name(),
                field.getName(),
                position.getName(),
                recruitment.getRecruitmentCount(),
                recruitment.getCurrentCount(),
                recruitment.isClosed(),
                techStacks
        );
    }
}