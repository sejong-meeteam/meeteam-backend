package com.wardk.meeteam_backend.web.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Administrator wire contracts. Null historical values are never fabricated. */
public final class AdminResponses {
    private AdminResponses() {}

    public record Page<T>(List<T> content, int number, int size, int numberOfElements,
                          long totalElements, boolean first, boolean last, boolean empty) {
        public static <T> Page<T> of(List<T> content, int number, int size, long total) {
            return new Page<>(content, number, size, content.size(), total, number == 0,
                    ((long) number + 1) * size >= total, content.isEmpty());
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MemberSummary(Long memberId, String name, String studentId, String email,
            String profileImageUrl, String representativePosition, String jobFieldCode,
            String jobFieldName, String role, String status, LocalDateTime suspendedUntil,
            long projectCount, LocalDateTime createdAt, LocalDateTime lastLoginAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MemberDetail(@JsonUnwrapped MemberSummary summary, LocalDate birthDate,
            String gender, String githubUrl, String blogUrl, String introduce,
            List<String> jobPositions, List<String> skills, String suspendReason,
            List<MemberProject> projects, List<MemberApplication> applications) {}

    public record MemberProject(Long projectId, String projectName, String category,
            boolean isLeader, String recruitmentStatus, String visibility, LocalDateTime createdAt) {}
    public record MemberApplication(Long applicationId, Long projectId, String projectName,
            String jobPositionName, String status, LocalDateTime appliedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProjectSummary(Long projectId, String name, String imageUrl, Long leaderId,
            String leaderName, String category, String platform, long currentMembers,
            long targetMembers, String recruitmentStatus, String visibility,
            long pendingApplicationCount, LocalDate deadline, LocalDateTime createdAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProjectDetail(@JsonUnwrapped ProjectSummary summary, String description,
            String githubUrl, String communicationUrl, List<ProjectMember> members,
            List<ProjectRecruitment> recruitments, Map<String, Long> applicationStats) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProjectMember(Long memberId, String name, String profileImageUrl,
            String jobPositionName, boolean isLeader) {}
    public record ProjectRecruitment(String jobFieldName, String jobPositionName,
            int recruitmentCount, int currentCount, boolean isClosed, List<String> techStacks) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AuditLog(Long id, Long adminId, String adminName, String action, String targetType,
            Long targetId, String targetName, String reason, Map<String, Object> metadata, LocalDateTime createdAt) {}
    public record DashboardStats(long totalMembers, long newMembersToday, long suspendedMembers,
            long totalProjects, long recruitingProjects, long hiddenProjects, long pendingApplications) {}
    public record Dashboard(DashboardStats stats, List<MemberSummary> recentMembers,
            List<ProjectSummary> recentProjects, List<AuditLog> recentAuditLogs) {}
}
