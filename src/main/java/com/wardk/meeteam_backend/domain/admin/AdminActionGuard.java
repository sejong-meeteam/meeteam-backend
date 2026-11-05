package com.wardk.meeteam_backend.domain.admin;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** Shared lock order: admin rows by ID, target member, then affected projects by ID. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AdminActionGuard {
    private final EntityManager em;
    public record Actor(Member member, LocalDateTime now) {}

    public Actor lockActor(Long id) {
        var admins = em.createQuery("select m from Member m where m.role = :role order by m.id", Member.class)
                .setParameter("role", UserRole.ADMIN).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for (Member admin : admins) em.refresh(admin, LockModeType.PESSIMISTIC_WRITE);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        Member actor = admins.stream().filter(m -> Objects.equals(m.getId(), id)
                && m.getRole() == UserRole.ADMIN && !m.isDeleted() && !m.isSuspendedAt(now))
                .findFirst().orElseThrow(() -> new CustomException(ErrorCode.ADMIN_FORBIDDEN));
        return new Actor(actor, now);
    }
    public Member lockMember(long id) {
        Member member = em.find(Member.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (member == null) throw new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND);
        em.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        return member;
    }
    public Project lockProject(long id) {
        Project project = em.find(Project.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (project == null) throw new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND);
        em.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        return project;
    }
    public long activeAdminCount(LocalDateTime now) {
        return em.createQuery("select count(m.id) from Member m where m.role = :role and m.deletedAt is null "
                + "and (m.suspended = false or (m.suspendedUntil is not null and m.suspendedUntil <= :now))", Long.class)
                .setParameter("role", UserRole.ADMIN).setParameter("now", now).getSingleResult();
    }
}
