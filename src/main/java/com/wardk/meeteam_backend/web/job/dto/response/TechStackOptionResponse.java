package com.wardk.meeteam_backend.web.job.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import lombok.Builder;

@Builder
public record TechStackOptionResponse(
        Long id,
        String name
) {
    public static TechStackOptionResponse of(
            TechStack techStack
    ) {
        return new TechStackOptionResponse(
                techStack.getId(),
                techStack.getName()
        );
    }
}
