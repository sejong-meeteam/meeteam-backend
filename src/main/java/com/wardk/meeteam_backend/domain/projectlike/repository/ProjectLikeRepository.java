package com.wardk.meeteam_backend.domain.projectlike.repository;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.projectlike.entity.ProjectLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProjectLikeRepository extends JpaRepository<ProjectLike, Long> {

    long deleteByMemberIdAndProjectId(Long memberId, Long projectId);

    @Query("SELECT COUNT(pl) FROM ProjectLike pl WHERE pl.project.id = :projectId AND pl.project.visibility = com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility.VISIBLE")
    Integer countByProjectId(Long projectId);
    Optional<ProjectLike> findByMemberAndProject(Member member, Project project);

    @Query("SELECT (COUNT(pl) > 0) FROM ProjectLike pl WHERE pl.member.id = :memberId AND pl.project.id = :projectId AND pl.project.visibility = com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility.VISIBLE")
    boolean existsByMemberIdAndProjectId(Long memberId, Long projectId);

    /**
     * 특정 회원이 좋아요한 프로젝트 ID 목록을 한 번에 조회 (N + 1 방지용 배치 쿼리)
     */
    @Query("SELECT pl.project.id FROM ProjectLike pl WHERE pl.member.id = :memberId AND pl.project.id IN :projectIds AND pl.project.visibility = com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility.VISIBLE")
    Set<Long> findLikedProjectIds(@Param("memberId") Long memberId, @Param("projectIds") List<Long> projectIds);

    /**
     * 특정 회원의 모든 좋아요 삭제
     */
    void deleteByMemberId(Long memberId);
}
