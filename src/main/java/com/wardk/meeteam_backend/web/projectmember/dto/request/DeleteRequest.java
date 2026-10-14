package com.wardk.meeteam_backend.web.projectmember.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class DeleteRequest {

    @NotNull
    private Long projectId;

    @NotNull
    private Long memberId;
}
