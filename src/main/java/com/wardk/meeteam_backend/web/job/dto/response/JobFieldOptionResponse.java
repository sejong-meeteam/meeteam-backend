package com.wardk.meeteam_backend.web.job.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.JobField;

import java.util.List;

/**
 * 직군(JobField) 옵션 응답 DTO.
 * 해당 직군에 속한 직무(positions)와 기술스택(techStacks)을 포함합니다.
 */
public record JobFieldOptionResponse(
        String code,
        String name,
        List<JobPositionOptionResponse> positions,
        List<TechStackOptionResponse> techStacks
) {
    public static JobFieldOptionResponse of(
            JobField jobField,
            List<JobPositionOptionResponse> positions,
            List<TechStackOptionResponse> techStacks
    ) {
        return new JobFieldOptionResponse(
                jobField.getCode().name(),
                jobField.getName(),
                positions,
                techStacks
        );
    }
}
