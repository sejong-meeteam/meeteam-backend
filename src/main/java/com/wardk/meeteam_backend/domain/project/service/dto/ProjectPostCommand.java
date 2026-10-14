package com.wardk.meeteam_backend.domain.project.service.dto;

import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.RecruitmentDeadlineType;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 등록을 위한 도메인 커맨드 DTO.
 * 웹 계층과 분리된 도메인 전용 DTO입니다.
 * 생성자의 직무 포지션은 ENUM 코드로 지정합니다.
 */
public record ProjectPostCommand(
        String projectName,
        String githubRepositoryUrl,
        String communicationChannelUrl,
        ProjectCategory projectCategory,
        String description,
        PlatformCategory platformCategory,
        JobPositionCode creatorJobPositionCode,
        List<RecruitmentCommand> recruitments,
        RecruitmentDeadlineType recruitmentDeadlineType,
        LocalDate endDate
) {
}
