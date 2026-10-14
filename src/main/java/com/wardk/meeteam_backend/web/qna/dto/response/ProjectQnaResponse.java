package com.wardk.meeteam_backend.web.qna.dto.response;

import com.wardk.meeteam_backend.domain.qna.entity.ProjectQna;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Q&A 응답 DTO.
 */
@Schema(description = "Q&A 정보")
public record ProjectQnaResponse(
        @Schema(description = "Q&A ID")
        Long qnaId,

        // 질문 정보
        @Schema(description = "질문자 ID")
        Long questionerId,

        @Schema(description = "질문자 이름")
        String questionerName,

        @Schema(description = "질문자 프로필 이미지 URL")
        String questionerProfileImageUrl,

        @Schema(description = "질문 내용")
        String question,

        @Schema(description = "질문 등록 시간")
        LocalDateTime createdAt,

        @Schema(description = "비밀글 여부")
        Boolean isSecret,

        // 답변 목록
        @Schema(description = "답변 목록")
        List<QnaAnswerResponse> answers
) {
    /**
     * 기본 변환 (비밀글 마스킹 없음 - 권한 있는 사용자용)
     */
    public static ProjectQnaResponse from(ProjectQna qna, Long leaderId) {
        List<QnaAnswerResponse> answerResponses = qna.getAnswers().stream()
                .map(answer -> QnaAnswerResponse.from(answer, leaderId))
                .toList();

        return new ProjectQnaResponse(
                qna.getId(),
                qna.getQuestioner().getId(),
                qna.getQuestioner().getRealName(),
                qna.getQuestioner().getStoreFileName(),
                qna.getQuestion(),
                qna.getCreatedAt(),
                qna.getIsSecret(),
                answerResponses
        );
    }

    /**
     * 비밀글 마스킹 처리 변환 (권한 없는 사용자용)
     */
    public static ProjectQnaResponse fromWithSecretMasking(ProjectQna qna, Long leaderId, Long viewerId) {
        boolean canView = !qna.getIsSecret() ||
                qna.getQuestioner().getId().equals(viewerId) ||
                leaderId.equals(viewerId);

        List<QnaAnswerResponse> answerResponses = qna.getAnswers().stream()
                .map(answer -> QnaAnswerResponse.from(answer, leaderId))
                .toList();

        return new ProjectQnaResponse(
                qna.getId(),
                qna.getQuestioner().getId(),
                canView ? qna.getQuestioner().getRealName() : "비밀글",
                canView ? qna.getQuestioner().getStoreFileName() : null,
                canView ? qna.getQuestion() : "비밀글입니다.",
                qna.getCreatedAt(),
                qna.getIsSecret(),
                canView ? answerResponses : List.of()
        );
    }
}
