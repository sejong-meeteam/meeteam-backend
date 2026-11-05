package com.wardk.meeteam_backend.domain.admin;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

/** Aggregate in SQL rather than loading every member/project into memory. */
@Repository
@RequiredArgsConstructor
public class AdminDashboardRepository {
    private final EntityManager em;
    public record MemberTotals(long total, long today, long suspended) {}
    public record ProjectTotals(long total, long recruiting, long hidden) {}

    public MemberTotals members(LocalDateTime now, LocalDateTime start, LocalDateTime end) {
        Object[] row = em.createQuery("select count(m.id), "
                + "coalesce(sum(case when m.createdAt >= :start and m.createdAt < :end then 1 else 0 end),0), "
                + "coalesce(sum(case when m.suspended = true and (m.suspendedUntil is null or m.suspendedUntil > :now) then 1 else 0 end),0) "
                + "from Member m where m.deletedAt is null", Object[].class)
                .setParameter("start", start).setParameter("end", end).setParameter("now", now).getSingleResult();
        return new MemberTotals(number(row[0]), number(row[1]), number(row[2]));
    }
    public ProjectTotals projects() {
        Object[] row = em.createQuery("select count(p.id), "
                + "coalesce(sum(case when p.visibility = :visible and p.recruitmentStatus = :recruiting then 1 else 0 end),0), "
                + "coalesce(sum(case when p.visibility = :hidden then 1 else 0 end),0) "
                + "from Project p where p.visibility <> :deleted", Object[].class)
                .setParameter("visible", ProjectVisibility.VISIBLE).setParameter("hidden", ProjectVisibility.HIDDEN)
                .setParameter("deleted", ProjectVisibility.DELETED).setParameter("recruiting", Recruitment.RECRUITING).getSingleResult();
        return new ProjectTotals(number(row[0]), number(row[1]), number(row[2]));
    }
    public long pendingApplications() {
        // Includes pending applications on moderated projects, matching the supplied frontend contract.
        return em.createQuery("select count(a.id) from ProjectApplication a where a.status = :status", Long.class)
                .setParameter("status", ApplicationStatus.PENDING).getSingleResult();
    }
    private long number(Object value) { return ((Number) value).longValue(); }
}
