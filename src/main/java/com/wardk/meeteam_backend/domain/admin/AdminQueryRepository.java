package com.wardk.meeteam_backend.domain.admin;

import com.wardk.meeteam_backend.domain.application.entity.*;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

/** HQL is built only from static fragments; all user values are bound parameters. */
@Repository
@RequiredArgsConstructor
public class AdminQueryRepository {
    private final EntityManager em;
    private static final String PROJECT_COUNT = "(select count(p.id) from Project p where p.isDeleted = false and "
            + "(p.creator.id = m.id or exists (select pm.id from ProjectMember pm where pm.project = p and pm.member = m)))";
    private static final String PENDING_COUNT = "(select count(a.id) from ProjectApplication a where a.project = p and a.status = "
            + "com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus.PENDING)";
    // Include the real creator once even if legacy data lacks their ProjectMember association.
    private static final String CURRENT_COUNT = "((select count(distinct pm.member.id) from ProjectMember pm where pm.project = p "
            + "and (p.creator is null or pm.member.id <> p.creator.id)) + case when p.creator is null then 0 else 1 end)";
    private static final String TARGET_COUNT = "(1 + (select coalesce(sum(r.recruitmentCount),0) from RecruitmentState r where r.project = p))";
    private static final String PROJECT_SELECT = "select p, " + CURRENT_COUNT + ", " + TARGET_COUNT + ", " + PENDING_COUNT;

    public record MemberRow(Member member, long projectCount) {}
    public record ProjectRow(Project project, long currentMembers, long targetMembers, long pendingCount) {}
    public record Result<T>(List<T> content, long total) {}

    public Result<MemberRow> members(AdminQueryOptions options, String status, String role, String fieldCode) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" where 1=1");
        if (!options.keyword().isEmpty()) {
            where.append(" and (lower(m.realName) like :keyword escape '!' or lower(m.email) like :keyword escape '!' or lower(m.studentId) like :keyword escape '!')");
            params.put("keyword", options.keywordPattern());
        }
        if ("ACTIVE".equals(status)) {
            where.append(" and m.deletedAt is null and (m.suspended = false or (m.suspendedUntil is not null and m.suspendedUntil <= :now))");
            params.put("now", java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
        }
        if ("WITHDRAWN".equals(status)) where.append(" and m.deletedAt is not null");
        if ("SUSPENDED".equals(status)) {
            where.append(" and m.deletedAt is null and m.suspended = true and (m.suspendedUntil is null or m.suspendedUntil > :now)");
            params.put("now", java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
        }
        UserRole parsedRole = AdminQueryOptions.enumOrNull(UserRole.class, role);
        if (parsedRole != null) { where.append(" and m.role = :role"); params.put("role", parsedRole); }
        JobFieldCode field = AdminQueryOptions.enumOrNull(JobFieldCode.class, fieldCode);
        if (field != null) {
            // No persisted display order exists: smallest association ID is representative.
            where.append(" and exists (select j.id from MemberJobPosition j where j.member = m and j.id = "
                    + "(select min(j2.id) from MemberJobPosition j2 where j2.member = m) and j.jobPosition.jobField.code = :field)");
            params.put("field", field);
        }
        String order = "m.createdAt desc, m.id desc";
        if ("name,asc".equals(options.sort())) order = "m.realName asc, m.id asc";
        if ("projectCount,desc".equals(options.sort())) order = PROJECT_COUNT + " desc, m.id desc";
        if ("lastLoginAt,desc".equals(options.sort())) order = "m.lastLoginAt desc nulls last, m.id desc";
        long total = bind(em.createQuery("select count(m.id) from Member m" + where, Long.class), params).getSingleResult();
        List<MemberRow> rows = bind(em.createQuery("select m, " + PROJECT_COUNT + " from Member m" + where + " order by " + order, Object[].class), params)
                .setFirstResult(options.offset()).setMaxResults(options.size()).getResultList().stream()
                .map(r -> new MemberRow((Member) r[0], number(r[1]))).toList();
        return new Result<>(rows, total);
    }

    public Optional<MemberRow> member(long id) {
        return em.createQuery("select m, " + PROJECT_COUNT + " from Member m where m.id = :id", Object[].class)
                .setParameter("id", id).getResultStream().findFirst()
                .map(r -> new MemberRow((Member) r[0], number(r[1])));
    }

    public Result<ProjectRow> projects(AdminQueryOptions options, String category, String recruitmentStatus, String visibility) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" where 1=1");
        if (!options.keyword().isEmpty()) {
            where.append(" and (lower(p.name) like :keyword escape '!' or lower(leader.realName) like :keyword escape '!')");
            params.put("keyword", options.keywordPattern());
        }
        ProjectCategory parsedCategory = AdminQueryOptions.enumOrNull(ProjectCategory.class, category);
        if (parsedCategory != null) { where.append(" and p.projectCategory = :category"); params.put("category", parsedCategory); }
        Recruitment recruitment = AdminQueryOptions.enumOrNull(Recruitment.class, recruitmentStatus);
        if (recruitment != null) { where.append(" and p.recruitmentStatus = :recruitment"); params.put("recruitment", recruitment); }
        ProjectVisibility parsedVisibility = AdminQueryOptions.enumOrNull(ProjectVisibility.class, visibility);
        if (parsedVisibility != null) { where.append(" and p.visibility = :visibility"); params.put("visibility", parsedVisibility); }
        String order = "p.createdAt desc, p.id desc";
        if ("name,asc".equals(options.sort())) order = "p.name asc, p.id asc";
        if ("pendingApplicationCount,desc".equals(options.sort())) order = PENDING_COUNT + " desc, p.id desc";
        long total = bind(em.createQuery("select count(p.id) from Project p left join p.creator leader" + where, Long.class), params).getSingleResult();
        List<ProjectRow> rows = bind(em.createQuery(PROJECT_SELECT + " from Project p left join fetch p.creator leader" + where + " order by " + order, Object[].class), params)
                .setFirstResult(options.offset()).setMaxResults(options.size()).getResultList().stream()
                .map(this::projectRow).toList();
        return new Result<>(rows, total);
    }

    public Optional<ProjectRow> project(long id) {
        return em.createQuery(PROJECT_SELECT + " from Project p left join fetch p.creator where p.id = :id", Object[].class)
                .setParameter("id", id).getResultStream().findFirst().map(this::projectRow);
    }

    /** Fetch only to-one chains after pagination. No collection fetch can trigger in-memory paging. */
    public List<MemberJobPosition> positions(List<Long> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        return em.createQuery("select j from MemberJobPosition j join fetch j.jobPosition pos join fetch pos.jobField "
                + "where j.member.id in :ids order by j.id", MemberJobPosition.class).setParameter("ids", memberIds).getResultList();
    }
    public List<String> skills(long memberId) {
        return em.createQuery("select s.techStack.name from MemberTechStack s where s.member.id = :id order by s.displayOrder, s.id", String.class)
                .setParameter("id", memberId).getResultList();
    }
    public List<Project> memberProjects(long memberId) {
        return em.createQuery("select p from Project p left join fetch p.creator where p.isDeleted = false and "
                + "(p.creator.id = :id or exists (select pm.id from ProjectMember pm where pm.project = p and pm.member.id = :id)) "
                + "order by p.createdAt desc, p.id desc", Project.class).setParameter("id", memberId).getResultList();
    }
    public List<ProjectApplication> memberApplications(long memberId) {
        return em.createQuery("select a from ProjectApplication a join fetch a.project join fetch a.jobPosition "
                + "where a.applicant.id = :id order by a.createdAt desc, a.id desc", ProjectApplication.class)
                .setParameter("id", memberId).getResultList();
    }
    public List<ProjectMember> projectMembers(long projectId) {
        return em.createQuery("select pm from ProjectMember pm join fetch pm.member left join fetch pm.jobPosition "
                + "where pm.project.id = :id order by pm.id", ProjectMember.class).setParameter("id", projectId).getResultList();
    }
    public List<RecruitmentState> recruitments(long projectId) {
        return em.createQuery("select distinct r from RecruitmentState r join fetch r.jobPosition pos join fetch pos.jobField "
                + "left join fetch r.recruitmentTechStacks ts left join fetch ts.techStack where r.project.id = :id order by r.id", RecruitmentState.class)
                .setParameter("id", projectId).getResultList();
    }
    public Map<String, Long> applicationStats(long projectId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ApplicationStatus value : ApplicationStatus.values()) result.put(value.name(), 0L);
        em.createQuery("select a.status, count(a.id) from ProjectApplication a where a.project.id = :id group by a.status", Object[].class)
                .setParameter("id", projectId).getResultList().forEach(r -> result.put(((ApplicationStatus) r[0]).name(), number(r[1])));
        return result;
    }
    public List<MemberRow> recentMembers() {
        return em.createQuery("select m, " + PROJECT_COUNT + " from Member m where m.deletedAt is null order by m.createdAt desc, m.id desc", Object[].class)
                .setMaxResults(5).getResultList().stream().map(row -> new MemberRow((Member) row[0], number(row[1]))).toList();
    }
    public List<ProjectRow> recentProjects() {
        return em.createQuery(PROJECT_SELECT + " from Project p left join fetch p.creator where p.visibility <> :deleted order by p.createdAt desc, p.id desc", Object[].class)
                .setParameter("deleted", ProjectVisibility.DELETED).setMaxResults(5).getResultList().stream().map(this::projectRow).toList();
    }
    private ProjectRow projectRow(Object[] r) { return new ProjectRow((Project) r[0], number(r[1]), number(r[2]), number(r[3])); }
    private static long number(Object value) { return ((Number) value).longValue(); }
    private static <T> TypedQuery<T> bind(TypedQuery<T> query, Map<String, Object> params) {
        params.forEach(query::setParameter);
        return query;
    }
}
