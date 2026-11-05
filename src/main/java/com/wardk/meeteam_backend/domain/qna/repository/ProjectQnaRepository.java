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

    @Query("SELECT q FROM ProjectQna q WHERE q.id = :id AND q.project.id = :projectId AND q.project.visibility = com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility.VISIBLE")
    java.util.Optional<ProjectQna> findByIdAndProjectId(@Param("id") Long id, @Param("projectId") Long projectId);

    /**
     * 프로젝트별 Q&A 목록 조회 (최신순, 페이징)
     */
    @Query("SELECT q FROM ProjectQna q " +
           "JOIN FETCH q.questioner " +
           "WHERE q.project.id = :projectId AND q.project.visibility = com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility.VISIBLE " +
           "ORDER BY q.createdAt DESC")
    Page<ProjectQna> findByProjectIdWithQuestioner(@Param("projectId") Long projectId, Pageable pageable);

    /**
     * 프로젝트별 Q&A 개수 조회
     */
    @Query("SELECT COUNT(q) FROM ProjectQna q WHERE q.project.id = :projectId AND q.project.visibility = com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility.VISIBLE")
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
