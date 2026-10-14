package com.wardk.meeteam_backend.domain.project.service.dto;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;

import java.util.List;

/**
 * 프로젝트 수정 시 모집 정보 도메인 커맨드 DTO.
 * recruitmentStateId가 null이면 신규 포지션 추가, 아니면 기존 포지션 수정.
 */
public record RecruitmentEditCommand(
        Long recruitmentStateId,
        JobFieldCode jobFieldCode,
        JobPositionCode jobPositionCode,
        Integer recruitmentCount,
        List<Long> techStackIds
) {
    /**
     * 신규 포지션 추가인지 확인합니다.
     */
    public boolean isNewPosition() {
        return recruitmentStateId == null;
    }
}
