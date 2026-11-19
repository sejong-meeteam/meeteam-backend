package com.wardk.meeteam_backend.web.application.dto.response;

import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 프로젝트 지원자 목록 응답 DTO.
 * 프로젝트의 실제 생성자가 인증된 지원자 목록을 조회할 때만 사용됩니다.
 */
@Schema(description = "프로젝트 지원자 목록 응답")
public record ProjectApplicationListResponse(
        @Schema(description = "지원서 ID (상세 보기, 승인/거절에 사용)")
        Long applicationId,

        @Schema(description = "지원자 ID")
        Long applicantId,

        @Schema(description = "지원자 이름")
        String applicantName,

        @Schema(description = "지원자 프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "지원자 이메일")
        String applicantEmail,

        @Schema(description = "직군명")
        String jobFieldName,

        @Schema(description = "포지션명")
        String jobPositionName,

        @Schema(description = "지원 사유 및 자기소개")
        String motivation,

        @Schema(description = "지원 상태")
        String status,

        @Schema(description = "지원 일시")
        LocalDateTime appliedAt,

        @Schema(description = "승인·거절 처리 일시 (PENDING이면 null)")
        LocalDateTime decidedAt,

        @Schema(description = "포지션 현재 모집 인원 (포지션이 삭제됐으면 null)")
        Integer currentCount,

        @Schema(description = "포지션 총 모집 인원 (포지션이 삭제됐으면 null)")
        Integer recruitmentCount,

        @Schema(description = "포지션 모집 마감 여부 (포지션이 삭제됐으면 null)")
        Boolean isRecruitmentFull
) {
    /**
     * Protected-context factory. Call only after the service has verified that the requester is
     * the actual creator of this application's project. {@code recruitmentState} is null when the
     * applied position no longer exists.
     */
    public static ProjectApplicationListResponse forAuthorizedProjectCreator(ProjectApplication application, RecruitmentState recruitmentState) {
        return new ProjectApplicationListResponse(
                application.getId(),
                application.getApplicant().getId(),
                application.getApplicant().getRealName(),
                application.getApplicant().getStoreFileName(),
                application.getApplicant().getEmail(),
                application.getJobPosition().getJobField().getName(),
                application.getJobPosition().getName(),
                application.getMotivation(),
                application.getStatus().getDisplayName(),
                application.getCreatedAt(),
                application.getDecidedAt(),
                recruitmentState == null ? null : recruitmentState.getCurrentCount(),
                recruitmentState == null ? null : recruitmentState.getRecruitmentCount(),
                recruitmentState == null ? null : recruitmentState.isClosed()
        );
    }
}
