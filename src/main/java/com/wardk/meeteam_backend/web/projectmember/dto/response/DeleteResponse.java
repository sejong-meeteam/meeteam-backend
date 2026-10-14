package com.wardk.meeteam_backend.web.projectmember.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeleteResponse {

    private Long projectId;
    private Long memberId;

    public static DeleteResponse responseDto(Long projectId, Long memberId) {
        return DeleteResponse.builder()
                .projectId(projectId)
                .memberId(memberId)
                .build();
    }
}
