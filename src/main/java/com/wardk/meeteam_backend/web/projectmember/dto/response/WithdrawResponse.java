package com.wardk.meeteam_backend.web.projectmember.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WithdrawResponse {

    private Long projectId;
    private Long memberId;

    public static WithdrawResponse responseDto(Long projectId, Long memberId) {
        return WithdrawResponse.builder()
                .projectId(projectId)
                .memberId(memberId)
                .build();
    }
}
