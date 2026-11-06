package com.wardk.meeteam_backend.domain.admin.service;

import com.wardk.meeteam_backend.domain.admin.entity.AdminAuditLog;
import com.wardk.meeteam_backend.domain.admin.entity.AuditAction;
import com.wardk.meeteam_backend.domain.admin.entity.AuditTargetType;
import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.policy.AdminActionGuard;
import com.wardk.meeteam_backend.domain.admin.policy.AdminReason;
import com.wardk.meeteam_backend.domain.admin.repository.AdminAuditWriter;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.ProjectSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Database-only moderation. GitHub collaborator changes are not implemented here. */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminProjectActionService {
    private final EntityManager em;
    private final AdminActionGuard guard;
    private final AdminAuditWriter audit;
    private final AdminQueryRepository queries;
    private final AdminResponseMapper mapper;

    public ProjectSummary close(Long adminId, long projectId, String reason) {
        String normalized = AdminReason.validate(reason);
        var actor = guard.lockActor(adminId);
        Project project = guard.lockProject(projectId);
        Recruitment previous = project.getRecruitmentStatus();
        if (previous == Recruitment.CLOSED) throw new CustomException(ErrorCode.ADMIN_INVALID_STATE);
        project.forceCloseRecruitment();
        append(actor, project, AuditAction.PROJECT_FORCE_CLOSE, normalized,
                Map.of("from", previous.name(), "to", Recruitment.CLOSED.name()));
        return summary(projectId);
    }

    public ProjectSummary expel(Long adminId, long projectId, long memberId, String reason) {
        String normalized = AdminReason.validate(reason);
        var actor = guard.lockActor(adminId);
        Project project = guard.lockProject(projectId);
        // The creator is the canonical leader even when a legacy roster has no leader row.
        if (project.getCreator() != null && Objects.equals(project.getCreator().getId(), memberId)) {
            throw new CustomException(ErrorCode.PROJECT_LEADER_EXPEL);
        }
        // Deliberately bypass ordinary repositories, which exclude hidden/deleted projects.
        var memberships = em.createQuery("select pm from ProjectMember pm where pm.project.id = :projectId "
                        + "and pm.member.id = :memberId order by pm.id", ProjectMember.class)
                .setParameter("projectId", projectId).setParameter("memberId", memberId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (memberships.isEmpty()) throw new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND);
        String memberName = memberships.get(0).getMember().getRealName();
        for (ProjectMember membership : memberships) {
            if (membership.getJobPosition() != null) {
                project.getRecruitments().stream()
                        .filter(r -> Objects.equals(r.getJobPosition().getId(), membership.getJobPosition().getId()))
                        .findFirst().ifPresent(r -> r.decreaseCurrentCount());
            }
            // Keep an already-loaded inverse collection consistent as well as the database.
            project.getMembers().remove(membership);
            em.remove(membership);
        }
        // This respects explicitly closed positions, including every force-closed position.
        project.updateRecruitmentStatusBasedOnPositions();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memberId", memberId);
        metadata.put("memberName", memberName);
        append(actor, project, AuditAction.PROJECT_MEMBER_EXPEL, normalized, metadata);
        return summary(projectId);
    }

    private void append(AdminActionGuard.Actor actor, Project project, AuditAction action,
                        String reason, Map<String, Object> metadata) {
        audit.append(new AdminAuditLog(actor.member().getId(), actor.member().getRealName(), action,
                AuditTargetType.PROJECT, project.getId(), project.getName(), reason, metadata, actor.now()));
    }

    private ProjectSummary summary(long projectId) {
        // Flush removals/count changes before the aggregate summary query.
        em.flush();
        return mapper.project(queries.project(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND)));
    }
}
