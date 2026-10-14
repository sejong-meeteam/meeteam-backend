package com.wardk.meeteam_backend.web.project.dto.request;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import jakarta.validation.constraints.NotNull;

public record RecruitmentPositionRequest(
        @NotNull
        JobPosition jobPosition,
        int count
) {
}
