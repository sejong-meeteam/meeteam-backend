package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 프로젝트 수정 완료 응답 DTO.
 */
@Builder
@Schema(description = "프로젝트 수정 완료 응답")
public record ProjectEditResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "프로젝트명", example = "MeeTeam 프로젝트")
        String name,

        @Schema(description = "모집 상태", example = "RECRUITING")
        Recruitment recruitmentStatus,

        @Schema(description = "자동 거절된 지원자 수 (포지션 삭제로 인한)", example = "3")
        int autoRejectedApplicantCount
) {
    /**
     * Project 엔티티로부터 응답을 생성합니다.
     */
    public static ProjectEditResponse from(Project project, int autoRejectedApplicantCount) {
        return ProjectEditResponse.builder()
                .projectId(project.getId())
                .name(project.getName())
                .recruitmentStatus(project.getRecruitmentStatus())
                .autoRejectedApplicantCount(autoRejectedApplicantCount)
                .build();
    }
}
