package com.wardk.meeteam_backend.web.projectmember.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
public class ProjectMemberListResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long memberId;
    private String name;
    private String imageUrl;
    private boolean isCreator;

    public static ProjectMemberListResponse responseDto(Long memberId, String name, String imageUrl, boolean isCreator) {
        return ProjectMemberListResponse.builder()
                .memberId(memberId)
                .name(name)
                .imageUrl(imageUrl)
                .isCreator(isCreator)
                .build();
    }
}
