package com.wardk.meeteam_backend.domain.recruitment.repository;


import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.web.recruitmentState.dto.response.ProjectCounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecruitmentStateRepository extends JpaRepository<RecruitmentState, Long> {

    @Query("""
    select new com.wardk.meeteam_backend.web.recruitmentState.dto.response.ProjectCounts(
        sum(pc.currentCount), sum(pc.recruitmentCount)
    )
    from RecruitmentState pc
    where pc.project = :project
    group by pc.project
    """)
    ProjectCounts findTotalCountsByProject(@Param("project") Project project);

    @Query("""
    SELECT rs FROM RecruitmentState rs
    JOIN FETCH rs.jobPosition jp
    JOIN FETCH jp.jobField
    WHERE rs.project.id = :projectId
    """)
    List<RecruitmentState> findByProjectIdWithJobPosition(@Param("projectId") Long projectId);

    /**
     * 해당 프로젝트에서 특정 포지션의 RecruitmentState 조회.
     */
    @Query("""
    SELECT rs FROM RecruitmentState rs
    WHERE rs.project.id = :projectId
    AND rs.jobPosition = :jobPosition
    """)
    Optional<RecruitmentState> findByProjectIdAndJobPosition(
            @Param("projectId") Long projectId,
            @Param("jobPosition") JobPosition jobPosition);

    /**
     * 여러 프로젝트이 모집 현황을 기술스택과 함께 한 번에 조회 (N + 1 방지용 배치 쿼리)
     */
    @Query("""
        SELECT rs FROM RecruitmentState rs
        JOIN FETCH rs.jobPosition jp
        JOIN FETCH jp.jobField
        LEFT JOIN FETCH rs.recruitmentTechStacks rts
        LEFT JOIN FETCH rts.techStack
        WHERE rs.project.id IN :projectIds
        """)
    List<RecruitmentState> findAllByProjectIdsWithDetails(@Param("projectIds") List<Long> projectIds);
}
