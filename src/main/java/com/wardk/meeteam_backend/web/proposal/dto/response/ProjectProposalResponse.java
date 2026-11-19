package com.wardk.meeteam_backend.web.proposal.dto.response;

import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.domain.proposal.entity.ProposalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Schema(description = "프로젝트 합류 제안 응답")
public record ProjectProposalResponse(
        Long proposalId,
        Long projectId,
        Long memberId,
        Long jobPositionId,
        String message,
        ProposalStatus status,
        @Schema(example = "2026-11-19T13:20:00+09:00")
        OffsetDateTime createdAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ProjectProposalResponse from(ProjectProposal proposal) {
        return new ProjectProposalResponse(
                proposal.getId(),
                proposal.getProject().getId(),
                proposal.getReceiver().getId(),
                proposal.getJobPosition().getId(),
                proposal.getMessage(),
                proposal.getStatus(),
                // Auditing stamps server-local time; the contract promises an explicit offset.
                proposal.getCreatedAt().atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(SEOUL).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS)
        );
    }
}
