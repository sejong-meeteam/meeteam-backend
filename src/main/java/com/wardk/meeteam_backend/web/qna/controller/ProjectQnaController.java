package com.wardk.meeteam_backend.web.qna.controller;

import com.wardk.meeteam_backend.domain.qna.service.ProjectQnaService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.qna.dto.request.QnaAnswerRequest;
import com.wardk.meeteam_backend.web.qna.dto.request.QnaQuestionRequest;
import com.wardk.meeteam_backend.web.qna.dto.response.ProjectQnaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 프로젝트 Q&A API 컨트롤러.
 */
@Tag(name = "Project Q&A", description = "프로젝트 Q&A 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/qna")
public class ProjectQnaController {

    private final ProjectQnaService projectQnaService;

    @Operation(
            summary = "Q&A 목록 조회",
            description = "프로젝트의 Q&A 목록을 조회합니다. 최신순으로 정렬되며 페이징을 지원합니다. 비밀글은 질문자와 프로젝트 리더만 내용을 볼 수 있습니다."
    )
    @Parameters({
            @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "페이지 크기", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "10")),
            @Parameter(name = "sort", description = "정렬 기준", in = ParameterIn.QUERY,
                    schema = @Schema(type = "string", defaultValue = "createdAt,desc"))
    })
    @GetMapping
    public SuccessResponse<Page<ProjectQnaResponse>> getQnaList(
            @PathVariable Long projectId,
            @Parameter(hidden = true)
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        Long viewerId = userDetails != null ? userDetails.getMemberId() : null;
        Page<ProjectQnaResponse> qnaList = projectQnaService.getQnaList(projectId, pageable, viewerId);
        return SuccessResponse.onSuccess(qnaList);
    }

    @Operation(
            summary = "질문 등록",
            description = "프로젝트에 질문을 등록합니다. 로그인한 회원만 질문할 수 있습니다. 비밀글로 등록하면 질문자와 프로젝트 리더만 내용을 볼 수 있습니다."
    )
    @PostMapping
    public SuccessResponse<ProjectQnaResponse> createQuestion(
            @PathVariable Long projectId,
            @RequestBody @Valid QnaQuestionRequest request,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        ProjectQnaResponse response = projectQnaService.createQuestion(
                projectId,
                userDetails.getMemberId(),
                request.question(),
                request.isSecret()
        );
        return SuccessResponse.onSuccess(response);
    }

    @Operation(
            summary = "답변 등록",
            description = "질문에 답변을 등록합니다. 질문자 본인 또는 프로젝트 리더만 답변할 수 있습니다."
    )
    @PostMapping("/{qnaId}/answer")
    public SuccessResponse<ProjectQnaResponse> addAnswer(
            @PathVariable Long projectId,
            @PathVariable Long qnaId,
            @RequestBody @Valid QnaAnswerRequest request,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        ProjectQnaResponse response = projectQnaService.addAnswer(
                projectId,
                qnaId,
                userDetails.getMemberId(),
                request.answer()
        );
        return SuccessResponse.onSuccess(response);
    }

    @Operation(
            summary = "질문 삭제",
            description = "Q&A 질문을 삭제합니다. 팀장은 모든 질문 삭제 가능, 일반 사용자는 본인 질문만 삭제 가능합니다. 단, 팀장의 답변이 달린 질문은 삭제할 수 없습니다."
    )
    @DeleteMapping("/{qnaId}")
    public SuccessResponse<Void> deleteQuestion(
            @PathVariable Long projectId,
            @PathVariable Long qnaId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        projectQnaService.deleteQuestion(projectId, qnaId, userDetails.getMemberId());
        return SuccessResponse.onSuccess(null);
    }

    @Operation(
            summary = "답변 삭제",
            description = "Q&A 답변을 삭제합니다. 팀장은 모든 답변 삭제 가능, 일반 사용자는 본인 답변만 삭제 가능합니다."
    )
    @DeleteMapping("/{qnaId}/answers/{answerId}")
    public SuccessResponse<Void> deleteAnswer(
            @PathVariable Long projectId,
            @PathVariable Long qnaId,
            @PathVariable Long answerId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        projectQnaService.deleteAnswer(projectId, qnaId, answerId, userDetails.getMemberId());
        return SuccessResponse.onSuccess(null);
    }
}
