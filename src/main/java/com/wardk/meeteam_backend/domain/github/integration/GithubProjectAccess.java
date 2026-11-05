package com.wardk.meeteam_backend.domain.github.integration;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class GithubProjectAccess {
    private final EntityManager em;
    public Project requirePublic(long projectId) {
        Project project = em.find(Project.class, projectId);
        if (project == null || !project.isPubliclyVisible()) throw new CustomException(ErrorCode.PROJECT_NOT_FOUND);
        return project;
    }
    public Member requireActive(Long memberId) {
        Member member = memberId == null ? null : em.find(Member.class, memberId);
        if (member == null || member.isDeleted() || member.isAdminWithdrawn() || member.isSuspendedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul"))))
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        return member;
    }
    public Project requireLeader(long projectId, Long memberId) {
        requireActive(memberId); Project project = requirePublic(projectId);
        if (!project.isLeader(memberId)) throw new CustomException(ErrorCode.GITHUB_FORBIDDEN, "프로젝트 관리 권한이 없습니다.");
        return project;
    }
    public Project lockLeader(long projectId, Long memberId) {
        if (memberId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        Member member = em.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE);
        if (member == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        em.refresh(member, LockModeType.PESSIMISTIC_WRITE); requireActive(memberId);
        Project project = em.find(Project.class, projectId, LockModeType.PESSIMISTIC_WRITE);
        if (project != null) em.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        return requireLeader(projectId, memberId);
    }
    public Project requireTeam(long projectId, Long memberId) {
        requireActive(memberId); Project project = requirePublic(projectId);
        if (!isTeam(projectId, memberId)) throw new CustomException(ErrorCode.GITHUB_FORBIDDEN, "프로젝트 팀원만 볼 수 있어요.");
        return project;
    }
    public boolean isTeam(long projectId, Long memberId) {
        if (memberId == null) return false;
        Member member = em.find(Member.class, memberId);
        if (member == null || member.isDeleted() || member.isSuspended()) return false;
        Project project = em.find(Project.class, projectId);
        if (project == null) return false;
        return project.isLeader(memberId) || em.createQuery("select count(pm) from ProjectMember pm where pm.project.id=:project and pm.member.id=:member", Long.class)
                .setParameter("project", projectId).setParameter("member", memberId).getSingleResult() > 0;
    }
}
