package com.wardk.meeteam_backend.web.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 팀원 방출 응답 DTO.
 */
@Schema(description = "팀원 방출 응답")
public record MemberExpelResponse(
        @Schema(description = "프로젝트 ID")
        Long projectId,

        @Schema(description = "방출된 멤버 ID")
        Long expelledMemberId,

        @Schema(description = "방출된 멤버 이름")
        String expelledMemberName
) {
    public static MemberExpelResponse of(Long projectId, Long memberId, String memberName) {
        return new MemberExpelResponse(projectId, memberId, memberName);
    }
}