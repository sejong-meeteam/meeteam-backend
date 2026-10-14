package com.wardk.meeteam_backend.web.projectlike.dto.request;

import lombok.Data;

@Data
public class ProjectLikeCountRequest {

    private Long projectId;

    private Integer likeCount;
}
