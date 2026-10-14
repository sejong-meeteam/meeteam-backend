package com.wardk.meeteam_backend.web.job.dto.response;

import java.util.List;

/**
 * 직군/직무/기술스택 옵션 응답 DTO.
 * 프로젝트 등록, 회원가입 시 선택 옵션을 제공합니다.
 */
public record JobOptionResponse(
        List<JobFieldOptionResponse> fields
) {
    public static JobOptionResponse of(List<JobFieldOptionResponse> fields) {
        return new JobOptionResponse(fields);
    }
}
