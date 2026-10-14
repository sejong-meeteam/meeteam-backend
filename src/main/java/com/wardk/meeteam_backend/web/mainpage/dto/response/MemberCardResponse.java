package com.wardk.meeteam_backend.web.mainpage.dto.response;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Comparator;
import java.util.List;

/**
 * 메인페이지 유저 카드 응답 DTO.
 */
@Schema(description = "메인페이지 유저 카드 정보")
public record MemberCardResponse(
        @Schema(description = "회원 ID")
        Long memberId,

        @Schema(description = "프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "직군명 (예: 프론트엔드)")
        String jobFieldName,

        @Schema(description = "회원 이름")
        String name,

        @Schema(description = "참여중인 프로젝트 수")
        Integer projectCount,

        @Schema(description = "기술스택 목록 (displayOrder 순)")
        List<TechStackInfo> techStacks
) {
    /**
     * 기술스택 정보.
     */
    @Schema(description = "기술스택 정보")
    public record TechStackInfo(
            @Schema(description = "기술스택 ID")
            Long id,

            @Schema(description = "기술스택명")
            String name,

            @Schema(description = "표시 순서")
            Integer displayOrder
    ) {}

    /**
     * Member 엔티티로부터 MemberCardResponse 생성.
     */
    public static MemberCardResponse from(Member member) {
        String jobFieldName = member.getJobPositions().isEmpty()
                ? null
                : member.getJobPositions().get(0).getJobPosition().getJobField().getName();

        List<TechStackInfo> techStacks = member.getMemberTechStacks().stream()
                .sorted(Comparator.comparing(mts -> mts.getDisplayOrder()))
                .limit(3)
                .map(mts -> new TechStackInfo(
                        mts.getTechStack().getId(),
                        mts.getTechStack().getName(),
                        mts.getDisplayOrder()
                ))
                .toList();

        return new MemberCardResponse(
                member.getId(),
                member.getStoreFileName(),
                jobFieldName,
                member.getRealName(),
                member.getProjectMembers().size(),
                techStacks
        );
    }
}