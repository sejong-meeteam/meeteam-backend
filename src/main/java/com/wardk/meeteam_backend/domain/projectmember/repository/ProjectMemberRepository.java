package com.wardk.meeteam_backend.domain.projectmember.repository;

import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    boolean existsByProjectIdAndMemberId(Long projectId, Long memberId);

    Optional<ProjectMember> findByProjectIdAndMemberId(Long projectId, Long memberId);

    void deleteByProjectIdAndMemberId(Long projectId, Long memberId);

    @Query("SELECT pm FROM ProjectMember pm " +
            "JOIN FETCH pm.project p " +
            "WHERE pm.member.id = :memberId AND p.isDeleted = false")
    List<ProjectMember> findAllByMemberId(Long memberId);

    /**
     * 프로젝트의 모든 멤버를 조회합니다 (Member 정보 포함)
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.member m WHERE pm.project.id = :projectId")
    List<ProjectMember> findAllByProjectIdWithMember(Long projectId);

    /**
     * 프로젝트 리더를 조회합니다 (JobPosition, JobField 정보 포함)
     */
    @Query("""
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.member m
            JOIN FETCH pm.jobPosition jp
            JOIN FETCH jp.jobField jf
            WHERE pm.project.id = :projectId AND pm.role = 'LEADER'
            """)
    Optional<ProjectMember> findLeaderByProjectId(@Param("projectId") Long projectId);

    /**
     * 특정 회원의 모든 프로젝트 멤버십 삭제
     */
    void deleteByMemberId(Long memberId);
}
