package com.wardk.meeteam_backend.domain.project.service.dto;

import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.RecruitmentDeadlineType;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 수정을 위한 도메인 커맨드 DTO.
 */
public record ProjectEditCommand(
        String name,
        String description,
        ProjectCategory projectCategory,
        PlatformCategory platformCategory,
        String githubRepositoryUrl,
        String communicationChannelUrl,
        LocalDate endDate,
        RecruitmentDeadlineType recruitmentDeadlineType,
        JobPositionCode leaderJobPositionCode,
        List<RecruitmentEditCommand> recruitments,
        boolean confirmDeletePositionsWithPendingApplicants
) {
}
