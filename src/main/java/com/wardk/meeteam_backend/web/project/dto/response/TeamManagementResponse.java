package com.wardk.meeteam_backend.web.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 프로젝트 팀원 관리 페이지 응답 DTO.
 */
@Getter
@Builder
@Schema(description = "팀원 관리 정보")
public class TeamManagementResponse {

    @Schema(description = "현재 팀원 수")
    private int currentMemberCount;

    @Schema(description = "총 모집 정원")
    private int totalRecruitmentCount;

    @Schema(description = "대기중인 지원서 수")
    private int pendingApplicationCount;

    @Schema(description = "팀원 목록")
    private List<TeamMemberInfo> members;

    @Getter
    @Builder
    @Schema(description = "팀원 정보")
    public static class TeamMemberInfo {

        @Schema(description = "회원 ID")
        private Long memberId;

        @Schema(description = "이름")
        private String name;

        @Schema(description = "프로필 이미지 URL")
        private String profileImageUrl;

        @Schema(description = "직군 이름", example = "백엔드")
        private String jobFieldName;

        @Schema(description = "포지션 이름", example = "Java/Spring")
        private String jobPositionName;

        @Schema(description = "리더 여부")
        private boolean isLeader;
    }
}