package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;

/**
 * 모집 상태 변경 응답 DTO.
 */
public record RecruitmentStatusResponse(
        Long projectId,
        Recruitment recruitmentStatus,
        boolean isRecruiting
) {
    public static RecruitmentStatusResponse from(Project project) {
        return new RecruitmentStatusResponse(
                project.getId(),
                project.getRecruitmentStatus(),
                project.isRecruiting()
        );
    }
}