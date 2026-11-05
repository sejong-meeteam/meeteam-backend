package com.wardk.meeteam_backend.domain.admin;

import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.web.admin.AdminResponses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminResponseMapper {
    private final S3ImageUrlResolver images;

    public MemberSummary member(AdminQueryRepository.MemberRow row, JobPosition position, boolean mask) {
        return member(row, position, mask, java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
    }
    public MemberSummary member(AdminQueryRepository.MemberRow row, JobPosition position, boolean mask, java.time.LocalDateTime now) {
        Member m = row.member();
        boolean suspended = m.isSuspendedAt(now);
        return new MemberSummary(m.getId(), m.getRealName(), mask ? maskStudentId(m.getStudentId()) : m.getStudentId(),
                m.getEmail(), images.resolve(m.getStoreFileName()), position == null ? null : position.getName(),
                position == null ? null : position.getJobField().getCode().name(),
                position == null ? null : position.getJobField().getName(), m.getRole().name(),
                m.isDeleted() ? "WITHDRAWN" : suspended ? "SUSPENDED" : "ACTIVE",
                suspended ? m.getSuspendedUntil() : null, row.projectCount(), m.getCreatedAt(), m.getLastLoginAt());
    }

    public ProjectSummary project(AdminQueryRepository.ProjectRow row) {
        Project p = row.project();
        Member leader = p.getCreator();
        // Never invent an identity or emit null for required frontend leader fields.
        if (leader == null || leader.getId() == null) {
            throw new com.wardk.meeteam_backend.global.exception.CustomException(
                    com.wardk.meeteam_backend.global.response.ErrorCode.ADMIN_INVALID_STATE);
        }
        return new ProjectSummary(p.getId(), p.getName(), images.resolve(p.getImageUrl()),
                leader.getId(), leader.getRealName() == null ? "(이름 없음)" : leader.getRealName(),
                p.getCategoryCode(), p.getPlatformName(), row.currentMembers(), row.targetMembers(),
                p.getRecruitmentStatus().name(), visibility(p), row.pendingCount(), p.getEndDate(), p.getCreatedAt());
    }
    public com.wardk.meeteam_backend.web.admin.AdminResponses.ProjectMember projectMember(Member m, String position, boolean leader) {
        return new com.wardk.meeteam_backend.web.admin.AdminResponses.ProjectMember(m.getId(), m.getRealName(),
                images.resolve(m.getStoreFileName()), position, leader);
    }
    public static String maskStudentId(String id) {
        if (id == null || id.isEmpty()) return null;
        return id.substring(0, Math.min(4, id.length())) + "*".repeat(Math.max(0, id.length() - 4));
    }
    public static String visibility(Project p) { return p.getVisibility().name(); }
}
