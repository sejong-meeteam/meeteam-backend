package com.wardk.meeteam_backend.web.proposal.controller;

import com.wardk.meeteam_backend.domain.proposal.service.ProjectProposalService;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.response.ErrorResponse;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.proposal.dto.request.ProjectProposalRequest;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProjectProposalResponse;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProposableProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "프로젝트 합류 제안", description = "리더가 모집 중인 프로젝트로 회원에게 합류를 제안하는 API")
@RestController
@RequiredArgsConstructor
public class ProposalController {

    private final ProjectProposalService proposalService;

    @Operation(summary = "제안할 수 있는 내 프로젝트 조회",
            description = "내가 리더인 모집 중 프로젝트 가운데 이 회원에게 제안할 수 있는 프로젝트와 열린 포지션을 반환합니다.")
    @GetMapping("/api/v1/members/{memberId}/proposable-projects")
    public SuccessResponse<List<ProposableProjectResponse>> getProposableProjects(
            @PathVariable Long memberId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        return SuccessResponse.onSuccess(proposalService.getProposableProjects(userDetails.getMemberId(), memberId));
    }

    @Operation(summary = "프로젝트 합류 제안 보내기",
            description = "프로젝트 리더가 모집 중인 포지션으로 같은 학교 회원에게 합류를 제안합니다. 받는 회원에게 알림이 갑니다.")
    @PostMapping("/api/v1/projects/{projectId}/proposals")
    public SuccessResponse<ProjectProposalResponse> propose(
            @PathVariable Long projectId,
            @RequestBody ProjectProposalRequest request,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        return SuccessResponse.onSuccess(proposalService.propose(projectId, userDetails.getMemberId(), request.validated()));
    }

    /** The dialog shows the message verbatim, so an unparsable body gets the proposal wording, not a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        ErrorCode errorCode = ErrorCode.PROPOSAL_INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.getResponse(errorCode.getCode(), errorCode.getMessage()));
    }
}
