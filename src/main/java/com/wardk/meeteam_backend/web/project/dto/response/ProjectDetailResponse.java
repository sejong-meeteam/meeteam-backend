package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import com.wardk.meeteam_backend.domain.project.entity.RecruitmentDeadlineType;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 상세 조회 응답 DTO.
 * 프로젝트 소개 페이지에 필요한 모든 정보를 포함합니다.
 */
@Schema(description = "프로젝트 상세 정보")
public record ProjectDetailResponse(
        // 프로젝트 기본 정보
        @Schema(description = "프로젝트 ID")
        Long id,

        @Schema(description = "프로젝트 제목")
        String name,

        @Schema(description = "프로젝트 설명")
        String description,

        @Schema(description = "프로젝트 카테고리")
        ProjectCategory projectCategory,

        @Schema(description = "출시 플랫폼")
        PlatformCategory platformCategory,

        @Schema(description = "프로젝트 대표 이미지 URL")
        String imageUrl,

        @Schema(description = "모집 상태")
        Recruitment recruitmentStatus,

        @Schema(description = "마감 방식")
        RecruitmentDeadlineType recruitmentDeadlineType,

        @Schema(description = "프로젝트 시작일")
        LocalDate startDate,

        @Schema(description = "프로젝트 마감일")
        LocalDate endDate,

        // 외부 링크
        @Schema(description = "GitHub 저장소 URL")
        String githubRepositoryUrl,

        @Schema(description = "소통 채널 URL (디스코드, 슬랙 등)")
        String communicationChannelUrl,

        // 프로젝트 리더 정보
        @Schema(description = "프로젝트 리더 정보")
        ProjectLeaderResponse leader,

        // 모집 분야
        @Schema(description = "모집 분야 목록")
        List<RecruitmentDetailResponse> recruitments,

        // 참여 멤버
        @Schema(description = "현재 참여중인 멤버 목록")
        List<ProjectMemberInfo> members,

        // 좋아요 및 권한 정보
        @Schema(description = "좋아요 수")
        Integer likeCount,

        @Schema(description = "현재 사용자의 좋아요 여부 (비로그인 시 false)")
        Boolean isLiked,

        @Schema(description = "현재 사용자가 프로젝트 리더인지 여부 (비로그인 시 false)")
        Boolean isLeader
) {
    /**
     * 참여 멤버 정보.
     */
    @Schema(description = "참여 멤버 정보")
    public record ProjectMemberInfo(
            @Schema(description = "회원 ID")
            Long memberId,

            @Schema(description = "회원 이름")
            String name,

            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl
    ) {
        public static ProjectMemberInfo from(ProjectMember projectMember) {
            return new ProjectMemberInfo(
                    projectMember.getMember().getId(),
                    projectMember.getMember().getRealName(),
                    projectMember.getMember().getStoreFileName()
            );
        }
    }

    /**
     * 프로젝트 상세 응답 생성 (좋아요/리더 정보 포함)
     *
     * @param project   프로젝트 엔티티
     * @param isLiked   현재 사용자의 좋아요 여부
     * @param isLeader  현재 사용자가 리더인지 여부
     */
    public static ProjectDetailResponse from(Project project, boolean isLiked, boolean isLeader) {
        ProjectLeaderResponse leader = ProjectLeaderResponse.from(project.getCreator());

        List<RecruitmentDetailResponse> recruitments = project.getRecruitments().stream()
                .map(RecruitmentDetailResponse::from)
                .toList();

        List<ProjectMemberInfo> members = project.getMembers().stream()
                .map(ProjectMemberInfo::from)
                .toList();

        return new ProjectDetailResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getProjectCategory(),
                project.getPlatformCategory(),
                project.getImageUrl(),
                project.getRecruitmentStatus(),
                project.getRecruitmentDeadlineType(),
                project.getStartDate(),
                project.getEndDate(),
                project.getGithubRepositoryUrl(),
                project.getCommunicationChannelUrl(),
                leader,
                recruitments,
                members,
                project.getLikeCount(),
                isLiked,
                isLeader
        );
    }
}