package com.wardk.meeteam_backend.web.qna.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Q&A 답변 등록 요청 DTO.
 */
@Schema(description = "Q&A 답변 등록 요청")
public record QnaAnswerRequest(
        @NotBlank(message = "답변 내용은 필수입니다.")
        @Size(max = 2000, message = "답변은 2000자 이하로 작성해주세요.")
        @Schema(description = "답변 내용", example = "매주 토요일 오후 2시 강남역 부근에서 진행합니다!")
        String answer
) {
}
