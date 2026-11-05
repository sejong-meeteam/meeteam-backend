package com.wardk.meeteam_backend.domain.qna.repository;

import com.wardk.meeteam_backend.domain.qna.entity.QnaAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Q&A 답변 레포지토리.
 */
public interface QnaAnswerRepository extends JpaRepository<QnaAnswer, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT a FROM QnaAnswer a WHERE a.id = :id AND a.projectQna.id = :qnaId AND a.projectQna.project.visibility = com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility.VISIBLE")
    java.util.Optional<QnaAnswer> findByIdAndQnaId(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("qnaId") Long qnaId);

    /**
     * 특정 회원이 작성한 모든 답변 삭제
     */
    void deleteByWriterId(Long writerId);
}
