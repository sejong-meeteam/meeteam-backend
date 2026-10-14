package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.MemberJobPosition;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 관심분야(직군/직무) 응답 DTO.
 */
@Schema(description = "회원 관심분야 정보")
public record MemberJobPositionResponse(
        @Schema(description = "직군 코드", example = "BACKEND")
        String jobFieldCode,

        @Schema(description = "직군 이름", example = "백엔드")
        String jobFieldName,

        @Schema(description = "직무 이름", example = "Java/Spring")
        String jobPositionName
) {
    public static MemberJobPositionResponse from(MemberJobPosition memberJobPosition) {
        JobPosition position = memberJobPosition.getJobPosition();
        JobField field = position.getJobField();

        return new MemberJobPositionResponse(
                field.getCode().name(),
                field.getName(),
                position.getName()
        );
    }
}