package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 수정 페이지 Pre-fill 정보 응답 DTO.
 */
@Builder
@Schema(description = "프로젝트 수정 페이지 Pre-fill 정보")
public record ProjectEditPrefillResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "프로젝트명", example = "MeeTeam 프로젝트")
        String name,

        @Schema(description = "프로젝트 설명", example = "개발자 팀 매칭 플랫폼입니다.")
        String description,

        @Schema(description = "프로젝트 카테고리", example = "IT_SERVICE")
        ProjectCategory projectCategory,

        @Schema(description = "프로젝트 카테고리명", example = "IT 서비스")
        String projectCategoryName,

        @Schema(description = "출시 플랫폼", example = "WEB")
        PlatformCategory platformCategory,

        @Schema(description = "GitHub 레포지토리 URL", example = "https://github.com/username/repository")
        String githubRepositoryUrl,

        @Schema(description = "소통 채널 URL", example = "https://discord.gg/abc123")
        String communicationChannelUrl,

        @Schema(description = "프로젝트 마감일", example = "2025-12-31")
        LocalDate endDate,

        @Schema(description = "프로젝트 커버 이미지 URL")
        String imageUrl,

        @Schema(description = "리더 직군명", example = "백엔드")
        String leaderJobFieldName,

        @Schema(description = "리더 직무 포지션명", example = "Java/Spring")
        String leaderJobPositionName,

        @Schema(description = "모집 분야 목록")
        List<RecruitmentEditInfo> recruitments,

        @Schema(description = "수정 가능 여부", example = "true")
        boolean editable,

        @Schema(description = "수정 불가 사유 (editable이 false일 때)", example = "모집이 중단된 상태에서는 수정할 수 없습니다.")
        String notEditableReason
) {
    /**
     * Project 엔티티와 관련 정보로부터 Pre-fill 응답을 생성합니다.
     */
    public static ProjectEditPrefillResponse from(
            Project project,
            ProjectMember leader,
            List<RecruitmentEditInfo> recruitmentInfos
    ) {
        boolean editable = project.isEditable();
        String notEditableReason = null;

        if (!editable) {
            notEditableReason = "모집이 중단된 상태에서는 수정할 수 없습니다. 모집을 재개한 후 수정해주세요.";
        }

        return ProjectEditPrefillResponse.builder()
                .projectId(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .projectCategory(project.getProjectCategory())
                .projectCategoryName(project.getCategoryDisplayName())
                .platformCategory(project.getPlatformCategory())
                .githubRepositoryUrl(project.getGithubRepositoryUrl())
                .communicationChannelUrl(project.getCommunicationChannelUrl())
                .endDate(project.getEndDate())
                .imageUrl(project.getImageUrl())
                .leaderJobFieldName(leader.getJobPosition().getJobField().getName())
                .leaderJobPositionName(leader.getJobPosition().getName())
                .recruitments(recruitmentInfos)
                .editable(editable)
                .notEditableReason(notEditableReason)
                .build();
    }
}
