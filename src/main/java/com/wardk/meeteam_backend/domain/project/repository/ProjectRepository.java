package com.wardk.meeteam_backend.domain.project.repository;

import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectSkill;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.web.projectlike.dto.response.ProjectWithLikeDto;
import jakarta.persistence.Entity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OneToMany;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> , ProjectRepositoryCustom{

    @Query("SELECT p FROM Project p " +
            "WHERE p.id = :id AND p.isDeleted = false")
    Optional<Project> findActiveById(Long id);

    @Query("SELECT DISTINCT p FROM Project p " +
            "JOIN FETCH p.recruitments r " +
            "WHERE p.id = :projectId AND p.isDeleted = false")
    Optional<Project> findByIdWithRecruitment(Long projectId);

    @Query("SELECT DISTINCT p FROM Project p " +
            "JOIN FETCH p.creator " +
            "LEFT JOIN FETCH p.projectSkills ps " +
            "LEFT JOIN FETCH ps.skill " +
            "WHERE p.isDeleted = false")
    List<Project> findAllWithCreatorAndSkills();

    @Query("SELECT DISTINCT p FROM Project p " +
            "LEFT JOIN FETCH p.members pm " +
            "LEFT JOIN FETCH pm.member m " +
            "WHERE p.id = :projectId AND p.isDeleted = false")
    Optional<Project> findByIdWithMembers(Long projectId);

    // 필요시 추가로 skills 조회하는 별도 메서드
    @Query("SELECT p FROM Project p " +
            "JOIN FETCH p.projectSkills ps " +
            "JOIN FETCH ps.skill " +
            "WHERE p IN :projects AND p.isDeleted = false")
    List<Project> findProjectsWithSkills(@Param("projects") List<Project> projects);

    @Query("""
       SELECT new com.wardk.meeteam_backend.web.projectlike.dto.response.ProjectWithLikeDto(p, COUNT(pl))
       FROM Project p
       LEFT JOIN p.projectLikes pl
       WHERE p.id = :projectId AND p.isDeleted = false
       GROUP BY p
       """)
    Optional<ProjectWithLikeDto> findProjectWithLikeCount(@Param("projectId") Long projectId);

    // 테스트 용도
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update Project p set p.createdAt = :ts where p.id = :id"
    )
    void overrideTimestamps(
            @Param("id") Long id,
            @Param("ts") java.time.LocalDateTime ts
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id AND p.isDeleted = false")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);

    /**
     * 프로젝트 상세 조회용 (creator와 recruitments fetch join)
     */
    @Query("""
       SELECT DISTINCT p FROM Project p
       JOIN FETCH p.creator c
       LEFT JOIN FETCH p.recruitments r
       WHERE p.id = :projectId AND p.isDeleted = false
       """)
    Optional<Project> findProjectDetailById(@Param("projectId") Long projectId);

    /**
     * 프로젝트 수정용 조회 (creator, recruitments, jobPosition, jobField fetch join)
     * recruitmentTechStacks는 @BatchSize로 lazy loading 처리
     */
    @Query("""
       SELECT DISTINCT p FROM Project p
       JOIN FETCH p.creator c
       LEFT JOIN FETCH p.recruitments r
       LEFT JOIN FETCH r.jobPosition jp
       LEFT JOIN FETCH jp.jobField jf
       WHERE p.id = :projectId AND p.isDeleted = false
       """)
    Optional<Project> findProjectForEdit(@Param("projectId") Long projectId);

    /**
     * 특정 회원이 생성한 모든 프로젝트 조회
     */
    List<Project> findByCreatorId(Long creatorId);
}
