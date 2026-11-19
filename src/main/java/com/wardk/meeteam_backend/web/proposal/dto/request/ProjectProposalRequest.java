package com.wardk.meeteam_backend.web.proposal.dto.request;

import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로젝트 합류 제안 요청. 검증 순서와 문구는 프론트 Zod 스키마(projectProposalRequestSchema)와 같다.
 */
@Schema(description = "프로젝트 합류 제안 요청")
public record ProjectProposalRequest(
        @Schema(description = "제안받을 회원 ID", example = "203")
        Long memberId,

        @Schema(description = "이 프로젝트에서 모집 중인 포지션 ID", example = "1")
        Long jobPositionId,

        @Schema(description = "선택. 앞뒤 공백 제거 후 300자 이하", example = "기술 스택이 프로젝트와 잘 맞아서 제안드려요.")
        String message
) {
    public ProjectProposalRequest {
        message = message == null ? "" : message.strip();
    }

    public ProjectProposalRequest validated() {
        if (memberId == null || memberId <= 0) {
            throw new CustomException(ErrorCode.PROPOSAL_INVALID_REQUEST, "제안할 회원을 확인해 주세요.");
        }
        if (jobPositionId == null || jobPositionId <= 0) {
            throw new CustomException(ErrorCode.PROPOSAL_INVALID_REQUEST, "제안할 포지션을 선택해 주세요.");
        }
        if (message.length() > ProjectProposal.MESSAGE_MAX_LENGTH) {
            throw new CustomException(ErrorCode.PROPOSAL_INVALID_REQUEST,
                    "메시지는 " + ProjectProposal.MESSAGE_MAX_LENGTH + "자 이내로 작성해 주세요.");
        }
        return this;
    }
}
