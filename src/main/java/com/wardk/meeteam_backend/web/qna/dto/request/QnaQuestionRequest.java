package com.wardk.meeteam_backend.web.qna.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Q&A 질문 등록 요청 DTO.
 */
@Schema(description = "Q&A 질문 등록 요청")
public record QnaQuestionRequest(
        @NotBlank(message = "질문 내용은 필수입니다.")
        @Size(max = 1000, message = "질문은 1000자 이하로 작성해주세요.")
        @Schema(description = "질문 내용", example = "프로젝트 모임은 매주 몇 번 하나요?")
        String question,

        @Schema(description = "비밀글 여부", example = "false")
        Boolean isSecret
) {
}
