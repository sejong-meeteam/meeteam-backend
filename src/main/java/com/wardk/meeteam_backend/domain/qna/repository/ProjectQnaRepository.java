package com.wardk.meeteam_backend.domain.qna.repository;

import com.wardk.meeteam_backend.domain.qna.entity.ProjectQna;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 프로젝트 Q&A 레포지토리.
 */
public interface ProjectQnaRepository extends JpaRepository<ProjectQna, Long> {

    /**
     * 프로젝트별 Q&A 목록 조회 (최신순, 페이징)
     */
    @Query("SELECT q FROM ProjectQna q " +
           "JOIN FETCH q.questioner " +
           "WHERE q.project.id = :projectId " +
           "ORDER BY q.createdAt DESC")
    Page<ProjectQna> findByProjectIdWithQuestioner(@Param("projectId") Long projectId, Pageable pageable);

    /**
     * 프로젝트별 Q&A 개수 조회
     */
    long countByProjectId(Long projectId);

    /**
     * 특정 회원이 작성한 모든 Q&A 삭제
     */
    void deleteByQuestionerId(Long questionerId);

    /**
     * 특정 프로젝트의 모든 Q&A 삭제
     */
    void deleteByProjectId(Long projectId);
}
