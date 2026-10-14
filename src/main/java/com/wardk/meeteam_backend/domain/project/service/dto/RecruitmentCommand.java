package com.wardk.meeteam_backend.domain.project.service.dto;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;

import java.util.List;

/**
 * 모집 정보 생성을 위한 도메인 커맨드 DTO.
 * 웹 계층의 DTO와 분리하여 계층 간 의존성을 제거합니다.
 * JobField와 JobPosition은 ENUM 코드로 식별하여 환경 독립성과 타입 안전성을 보장합니다.
 */
public record RecruitmentCommand(
        JobFieldCode jobFieldCode,
        JobPositionCode jobPositionCode,
        Integer recruitmentCount,
        List<Long> techStackIds
) {
}