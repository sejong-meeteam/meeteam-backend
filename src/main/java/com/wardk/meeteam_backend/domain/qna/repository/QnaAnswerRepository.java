package com.wardk.meeteam_backend.domain.qna.repository;

import com.wardk.meeteam_backend.domain.qna.entity.QnaAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Q&A 답변 레포지토리.
 */
public interface QnaAnswerRepository extends JpaRepository<QnaAnswer, Long> {

    /**
     * 특정 회원이 작성한 모든 답변 삭제
     */
    void deleteByWriterId(Long writerId);
}
