package com.wardk.meeteam_backend.domain.admin.service;

import com.wardk.meeteam_backend.domain.admin.entity.AdminAuditLog;
import com.wardk.meeteam_backend.domain.admin.entity.AuditAction;
import com.wardk.meeteam_backend.domain.admin.entity.AuditTargetType;
import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.policy.AdminActionGuard;
import com.wardk.meeteam_backend.domain.admin.policy.AdminReason;
import com.wardk.meeteam_backend.domain.admin.repository.AdminAuditWriter;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole;
import com.wardk.meeteam_backend.global.auth.service.TestAccountService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.MemberSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Local database actions only. Future GitHub collaborator removal must use a post-commit/outbox hook. */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminMemberActionService {
    private final EntityManager em;
    private final AdminActionGuard guard;
    private final AdminAuditWriter audit;
    private final AdminQueryRepository queries;
    private final AdminResponseMapper mapper;

    public MemberSummary changeRole(Long adminId, long targetId, UserRole next, String reason) {
        String normalized = AdminReason.validate(reason);
        if (next == null) throw new CustomException(ErrorCode.ADMIN_INVALID_REQUEST);
        var actor = guard.lockActor(adminId);
        rejectSelf(adminId, targetId);
        Member target = guard.lockMember(targetId);
        if (target.isDeleted() || target.isAdminWithdrawn() || target.getRole() == next) {
            throw new CustomException(ErrorCode.ADMIN_INVALID_STATE);
        }
        // The opt-in runner requires this reserved identity to remain an undeleted USER on restart.
        rejectReservedIdentity(target);
        preserveLastAdmin(target, actor);
        UserRole previous = target.getRole();
        target.changeRole(next);
        append(actor, target, AuditAction.MEMBER_ROLE_CHANGE, normalized,
                Map.of("from", previous.name(), "to", next.name()));
        return summary(targetId);
    }

    // Discovery queries must see admissions committed while waiting for the member lock.
    // MySQL REPEATABLE_READ may otherwise retain an earlier snapshot from a cascading refresh.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MemberSummary withdraw(Long adminId, long targetId, String reason) {
        String normalized = AdminReason.validate(reason);
        var actor = guard.lockActor(adminId);
        rejectSelf(adminId, targetId);
        Member target = guard.lockMember(targetId);
        if (target.isDeleted() || target.isAdminWithdrawn()) throw new CustomException(ErrorCode.ADMIN_INVALID_STATE);
        rejectReservedIdentity(target);
        preserveLastAdmin(target, actor);

        // Member before projects, projects in ascending order, then dependent rows. Never acquire a member lock hereafter.
        // Raw JPQL intentionally includes HIDDEN and DELETED projects omitted by public repositories.
        SortedSet<Long> projectIds = new TreeSet<>();
        projectIds.addAll(em.createQuery("select p.id from Project p where p.creator.id = :id", Long.class)
                .setParameter("id", targetId).getResultList());
        projectIds.addAll(em.createQuery("select pm.project.id from ProjectMember pm where pm.member.id = :id", Long.class)
                .setParameter("id", targetId).getResultList());
        projectIds.addAll(em.createQuery("select a.project.id from ProjectApplication a where a.applicant.id = :id and a.status = :status", Long.class)
                .setParameter("id", targetId).setParameter("status", ApplicationStatus.PENDING).getResultList());
        List<Project> projects = projectIds.stream().map(guard::lockProject).toList();
        for (Project project : projects) {
            if (project.getCreator() != null && Objects.equals(project.getCreator().getId(), targetId)
                    && project.getVisibility() == ProjectVisibility.VISIBLE) {
                throw new CustomException(ErrorCode.MEMBER_HAS_LED_PROJECTS);
            }
        }
        var applications = em.createQuery("select a from ProjectApplication a where a.applicant.id = :id and a.status = :status order by a.id", ProjectApplication.class)
                .setParameter("id", targetId).setParameter("status", ApplicationStatus.PENDING)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        applications.forEach(ProjectApplication::cancel);
        var memberships = em.createQuery("select pm from ProjectMember pm where pm.member.id = :id order by pm.project.id, pm.id", ProjectMember.class)
                .setParameter("id", targetId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for (ProjectMember membership : memberships) {
            Project project = membership.getProject();
            boolean leader = membership.getRole() == ProjectMemberRole.LEADER
                    || (project.getCreator() != null && Objects.equals(project.getCreator().getId(), targetId));
            // Recruitment counts cover recruited teammates, never the separately-counted creator.
            if (!leader && membership.getJobPosition() != null) {
                project.getRecruitments().stream()
                        .filter(r -> Objects.equals(r.getJobPosition().getId(), membership.getJobPosition().getId()))
                        .findFirst().ifPresent(r -> r.decreaseCurrentCount());
            }
            project.getMembers().remove(membership);
            target.getProjectMembers().remove(membership);
            em.remove(membership);
        }
        for (Project project : projects) {
            // Do not reopen CLOSED or SUSPENDED projects, or explicitly closed positions.
            if (project.getRecruitmentStatus() == Recruitment.RECRUITING) project.updateRecruitmentStatusBasedOnPositions();
        }
        // Account identity removal is transactional; repository access revocation is a later feature.
        em.createQuery("delete from MemberGithubAccount a where a.memberId = :memberId")
                .setParameter("memberId", targetId).executeUpdate();
        target.forceWithdraw();
        append(actor, target, AuditAction.MEMBER_WITHDRAW, normalized,
                Map.of("cancelledApplications", applications.size(), "removedMemberships", memberships.size()));
        return summary(targetId);
    }

    private void rejectSelf(Long adminId, long targetId) {
        if (Objects.equals(adminId, targetId)) throw new CustomException(ErrorCode.ADMIN_SELF_ACTION);
    }

    private void rejectReservedIdentity(Member target) {
        if (TestAccountService.EMAIL.equals(target.getEmail()) || target.isLocalAdminAccount()) {
            throw new CustomException(ErrorCode.ADMIN_INVALID_STATE);
        }
    }

    private void preserveLastAdmin(Member target, AdminActionGuard.Actor actor) {
        if (target.getRole() == UserRole.ADMIN && !target.isSuspendedAt(actor.now())
                && guard.activeAdminCount(actor.now()) <= 1) throw new CustomException(ErrorCode.ADMIN_LAST_ADMIN);
    }

    private void append(AdminActionGuard.Actor actor, Member target, AuditAction action, String reason, Map<String, Object> metadata) {
        audit.append(new AdminAuditLog(actor.member().getId(), actor.member().getRealName(), action,
                AuditTargetType.MEMBER, target.getId(), target.getRealName(), reason, metadata, actor.now()));
    }

    private MemberSummary summary(long targetId) {
        em.flush();
        var row = queries.member(targetId).orElseThrow(() -> new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND));
        var positions = queries.positions(List.of(targetId));
        return mapper.member(row, positions.isEmpty() ? null : positions.get(0).getJobPosition(), true);
    }
}
