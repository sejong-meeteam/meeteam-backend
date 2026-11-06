package com.wardk.meeteam_backend.domain.admin.service;

import com.wardk.meeteam_backend.domain.admin.entity.AdminAuditLog;
import com.wardk.meeteam_backend.domain.admin.entity.AuditAction;
import com.wardk.meeteam_backend.domain.admin.entity.AuditTargetType;
import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.policy.AdminActionGuard;
import com.wardk.meeteam_backend.domain.admin.policy.AdminReason;
import com.wardk.meeteam_backend.domain.admin.repository.AdminAuditWriter;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.MemberSummary;
import com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.ProjectSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminMutationService {
    private final AdminActionGuard guard;
    private final AdminQueryRepository queries;
    private final AdminResponseMapper mapper;
    private final AdminAuditWriter audit;

    public MemberSummary suspend(Long adminId, long targetId, Integer days, String reason) {
        String normalized = AdminReason.validate(reason);
        if (days != null && (days < 1 || days > 365)) throw new CustomException(ErrorCode.ADMIN_INVALID_REQUEST);
        var actor = guard.lockActor(adminId);
        LocalDateTime now = actor.now();
        Member admin = actor.member();
        if (Objects.equals(adminId, targetId)) throw new CustomException(ErrorCode.ADMIN_SELF_ACTION);
        Member target = guard.lockMember(targetId);
        if (target.isDeleted() || target.isSuspendedAt(now)) throw new CustomException(ErrorCode.ADMIN_INVALID_STATE);
        long activeAdmins = guard.activeAdminCount(now);
        if (target.getRole() == UserRole.ADMIN && activeAdmins <= 1) throw new CustomException(ErrorCode.ADMIN_LAST_ADMIN);
        target.suspend(days, normalized, now);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("days", days);
        audit.append(new AdminAuditLog(admin.getId(), admin.getRealName(), AuditAction.MEMBER_SUSPEND,
                AuditTargetType.MEMBER, target.getId(), target.getRealName(), normalized, metadata, now));
        return summary(targetId);
    }

    public MemberSummary unsuspend(Long adminId, long targetId, String reason) {
        String normalized = AdminReason.validate(reason);
        var actor = guard.lockActor(adminId);
        LocalDateTime now = actor.now();
        Member admin = actor.member();
        Member target = guard.lockMember(targetId);
        if (target.isDeleted() || !target.isSuspendedAt(now)) throw new CustomException(ErrorCode.ADMIN_INVALID_STATE);
        target.unsuspend();
        audit.append(new AdminAuditLog(admin.getId(), admin.getRealName(), AuditAction.MEMBER_UNSUSPEND,
                AuditTargetType.MEMBER, target.getId(), target.getRealName(), normalized, null, now));
        return summary(targetId);
    }

    public ProjectSummary visibility(Long adminId, long projectId, ProjectVisibility visibility, String reason) {
        String normalized = AdminReason.validate(reason);
        if (visibility == null) throw new CustomException(ErrorCode.ADMIN_INVALID_REQUEST);
        var actor = guard.lockActor(adminId);
        LocalDateTime now = actor.now();
        Member admin = actor.member();
        Project project = guard.lockProject(projectId);
        ProjectVisibility previous = project.getVisibility();
        if (previous == visibility) throw new CustomException(ErrorCode.ADMIN_INVALID_STATE);
        project.changeVisibility(visibility);
        AuditAction action = switch (visibility) {
            case VISIBLE -> AuditAction.PROJECT_RESTORE;
            case HIDDEN -> AuditAction.PROJECT_HIDE;
            case DELETED -> AuditAction.PROJECT_DELETE;
        };
        audit.append(new AdminAuditLog(admin.getId(), admin.getRealName(), action, AuditTargetType.PROJECT,
                project.getId(), project.getName(), normalized, Map.of("from", previous.name(), "to", visibility.name()), now));
        return mapper.project(queries.project(projectId).orElseThrow(() -> new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND)));
    }

    private MemberSummary summary(long id) {
        var row = queries.member(id).orElseThrow(() -> new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND));
        var positions = queries.positions(List.of(id));
        return mapper.member(row, positions.isEmpty() ? null : positions.get(0).getJobPosition(), true);
    }
}
