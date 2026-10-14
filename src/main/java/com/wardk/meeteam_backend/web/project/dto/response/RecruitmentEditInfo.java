package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentTechStack;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * 프로젝트 수정용 모집 포지션 정보 응답 DTO.
 */
@Builder
@Schema(description = "프로젝트 수정용 모집 포지션 정보")
public record RecruitmentEditInfo(
        @Schema(description = "모집 상태 ID", example = "1")
        Long recruitmentStateId,

        @Schema(description = "직군 코드", example = "BACKEND")
        JobFieldCode jobFieldCode,

        @Schema(description = "직군명", example = "백엔드")
        String jobFieldName,

        @Schema(description = "직무 포지션 코드", example = "JAVA_SPRING")
        JobPositionCode jobPositionCode,

        @Schema(description = "직무 포지션명", example = "Java/Spring")
        String jobPositionName,

        @Schema(description = "모집 인원", example = "3")
        Integer recruitmentCount,

        @Schema(description = "현재 승인 인원", example = "2")
        Integer currentCount,

        @Schema(description = "대기 지원자 수", example = "1")
        Integer pendingApplicationCount,

        @Schema(description = "기술 스택 ID 목록", example = "[30, 31, 38]")
        List<Long> techStackIds,

        @Schema(description = "기술 스택명 목록", example = "[\"Java\", \"Spring\", \"JPA\"]")
        List<String> techStackNames,

        @Schema(description = "삭제 가능 여부", example = "false")
        boolean deletable,

        @Schema(description = "삭제 불가 사유 (deletable이 false일 때)", example = "승인된 팀원이 있는 포지션은 삭제할 수 없습니다.")
        String notDeletableReason,

        @Schema(description = "최소 모집 인원 (현재 승인 인원)", example = "2")
        Integer minRecruitmentCount
) {
    /**
     * RecruitmentState와 대기 지원자 수로부터 RecruitmentEditInfo를 생성합니다.
     */
    public static RecruitmentEditInfo from(RecruitmentState recruitment, int pendingApplicationCount) {
        boolean deletable = recruitment.getCurrentCount() == 0;
        String notDeletableReason = null;

        if (!deletable) {
            notDeletableReason = "승인된 팀원이 있는 포지션은 삭제할 수 없습니다.";
        }

        List<Long> techStackIds = recruitment.getRecruitmentTechStacks().stream()
                .map(rts -> rts.getTechStack().getId())
                .toList();

        List<String> techStackNames = recruitment.getRecruitmentTechStacks().stream()
                .map(rts -> rts.getTechStack().getName())
                .toList();

        return RecruitmentEditInfo.builder()
                .recruitmentStateId(recruitment.getId())
                .jobFieldCode(recruitment.getJobField().getCode())
                .jobFieldName(recruitment.getJobField().getName())
                .jobPositionCode(recruitment.getJobPosition().getCode())
                .jobPositionName(recruitment.getJobPosition().getName())
                .recruitmentCount(recruitment.getRecruitmentCount())
                .currentCount(recruitment.getCurrentCount())
                .pendingApplicationCount(pendingApplicationCount)
                .techStackIds(techStackIds)
                .techStackNames(techStackNames)
                .deletable(deletable)
                .notDeletableReason(notDeletableReason)
                .minRecruitmentCount(recruitment.getCurrentCount())
                .build();
    }
}
