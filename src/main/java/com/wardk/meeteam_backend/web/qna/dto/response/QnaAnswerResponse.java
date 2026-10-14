package com.wardk.meeteam_backend.web.qna.dto.response;

import com.wardk.meeteam_backend.domain.qna.entity.QnaAnswer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Q&A 답변 응답 DTO.
 */
@Schema(description = "Q&A 답변 정보")
public record QnaAnswerResponse(
        @Schema(description = "답변 ID")
        Long answerId,

        @Schema(description = "작성자 ID")
        Long writerId,

        @Schema(description = "작성자 이름")
        String writerName,

        @Schema(description = "작성자 프로필 이미지 URL")
        String writerProfileImageUrl,

        @Schema(description = "리더 여부")
        boolean isLeader,

        @Schema(description = "답변 내용")
        String content,

        @Schema(description = "작성 시간")
        LocalDateTime createdAt
) {
    public static QnaAnswerResponse from(QnaAnswer answer, Long leaderId) {
        boolean isLeader = answer.getWriter().getId().equals(leaderId);

        return new QnaAnswerResponse(
                answer.getId(),
                answer.getWriter().getId(),
                answer.getWriter().getRealName(),
                answer.getWriter().getStoreFileName(),
                isLeader,
                answer.getContent(),
                answer.getCreatedAt()
        );
    }
}
