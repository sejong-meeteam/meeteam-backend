package com.wardk.meeteam_backend.domain.application.repository;

import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectApplicationRepository extends JpaRepository<ProjectApplication, Long> {

    boolean existsByProjectAndApplicant(Project project, Member member);

    @Query("""
            SELECT a FROM ProjectApplication a
            JOIN FETCH a.applicant
            JOIN FETCH a.jobPosition jp
            JOIN FETCH jp.jobField
            WHERE a.project.id = :projectId
            AND a.project.isDeleted = false
            AND a.status = 'PENDING'
            ORDER BY a.createdAt DESC
            """)
    List<ProjectApplication> findPendingByProjectIdOrderByCreatedAtDesc(Long projectId);

    @Query("SELECT a FROM ProjectApplication a " +
            "JOIN FETCH a.project p " +
            "WHERE a.applicant.id = :applicantId AND p.isDeleted = false")
    List<ProjectApplication> findAllByApplicantId(Long applicantId);

    @Query("""
            SELECT a FROM ProjectApplication a
            JOIN FETCH a.applicant
            JOIN FETCH a.jobPosition jp
            JOIN FETCH jp.jobField
            WHERE a.project.id = :projectId
            AND a.id = :applicationId
            AND a.project.isDeleted = false
            """)
    Optional<ProjectApplication> findByIdWithApplicantAndJobPosition(Long projectId, Long applicationId);

    /**
     * 특정 프로젝트의 특정 직무 포지션에 대한 대기중인 지원서를 조회합니다.
     */
    @Query("""
            SELECT a FROM ProjectApplication a
            JOIN FETCH a.applicant
            WHERE a.project.id = :projectId
            AND a.jobPosition.id = :jobPositionId
            AND a.status = 'PENDING'
            AND a.project.isDeleted = false
            """)
    List<ProjectApplication> findPendingByProjectIdAndJobPositionId(
            @Param("projectId") Long projectId,
            @Param("jobPositionId") Long jobPositionId
    );

    /**
     * 특정 프로젝트의 직무 포지션별 대기중인 지원자 수를 조회합니다.
     */
    @Query("""
            SELECT a.jobPosition.id, COUNT(a)
            FROM ProjectApplication a
            WHERE a.project.id = :projectId
            AND a.status = 'PENDING'
            AND a.project.isDeleted = false
            GROUP BY a.jobPosition.id
            """)
    List<Object[]> countPendingByProjectIdGroupByJobPositionId(@Param("projectId") Long projectId);

    /**
     * 특정 회원이 지원한 모든 지원서 삭제
     */
    void deleteByApplicantId(Long applicantId);
}
