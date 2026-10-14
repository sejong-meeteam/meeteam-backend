package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.MemberTechStack;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Comparator;
import java.util.List;

/**
 * 프로젝트 리더 정보 응답 DTO.
 */
@Schema(description = "프로젝트 리더 정보")
public record ProjectLeaderResponse(
        @Schema(description = "리더 ID")
        Long id,

        @Schema(description = "리더 이름")
        String name,

        @Schema(description = "프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "관심분야 목록 (직군/직무)")
        List<MemberJobPositionResponse> jobPositions,

        @Schema(description = "기술스택 목록 (displayOrder 순)")
        List<String> techStacks
) {
    public static ProjectLeaderResponse from(Member leader) {
        List<MemberJobPositionResponse> jobPositions = leader.getJobPositions().stream()
                .map(MemberJobPositionResponse::from)
                .toList();

        List<String> techStacks = leader.getMemberTechStacks().stream()
                .sorted(Comparator.comparing(MemberTechStack::getDisplayOrder))
                .map(mts -> mts.getTechStack().getName())
                .toList();

        return new ProjectLeaderResponse(
                leader.getId(),
                leader.getRealName(),
                leader.getStoreFileName(),
                jobPositions,
                techStacks
        );
    }
}