package com.wardk.meeteam_backend.web.proposal.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "제안할 수 있는 내 프로젝트")
public record ProposableProjectResponse(
        Long projectId,
        String projectName,
        List<Position> openPositions,
        @Schema(description = "이 회원에게 보낸 대기 중 제안이 이미 있는지")
        boolean hasPendingProposal
) {
    public record Position(Long jobPositionId, String jobFieldName, String jobPositionName) {
        static Position from(RecruitmentState recruitment) {
            var position = recruitment.getJobPosition();
            return new Position(position.getId(), position.getJobField().getName(), position.getName());
        }
    }

    public static ProposableProjectResponse of(Project project, boolean hasPendingProposal) {
        return new ProposableProjectResponse(
                project.getId(),
                project.getName(),
                project.getOpenRecruitments().stream().map(Position::from).toList(),
                hasPendingProposal
        );
    }
}
