package com.wardk.meeteam_backend.web.job.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;

/**
 * 직무(JobPosition) 옵션 응답 DTO.
 */
public record JobPositionOptionResponse(
        Long id,
        String code,
        String name
) {
    public static JobPositionOptionResponse of(JobPosition jobPosition) {
        return new JobPositionOptionResponse(
                jobPosition.getId(),
                jobPosition.getCode().name(),
                jobPosition.getName()
        );
    }
}
