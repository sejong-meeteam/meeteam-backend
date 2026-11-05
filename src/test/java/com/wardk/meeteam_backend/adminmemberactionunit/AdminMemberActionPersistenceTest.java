package com.wardk.meeteam_backend.adminmemberactionunit;

import com.wardk.meeteam_backend.domain.admin.*;
import com.wardk.meeteam_backend.domain.application.entity.*;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.job.entity.*;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.projectmember.entity.*;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.global.auth.service.TestAccountService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = AdminMemberActionPersistenceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminMemberActionPersistenceTest {
    @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
    @Import({AdminMemberActionService.class, AdminActionGuard.class, AdminAuditWriter.class,
            AdminQueryRepository.class, AdminResponseMapper.class, AdminAuditQueryService.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
    }
    @Autowired EntityManager em;
    @Autowired AdminMemberActionService service;
    @Autowired AdminAuditQueryService logs;
    @Autowired PlatformTransactionManager manager;
    @MockBean S3ImageUrlResolver images;
    @SpyBean AdminAuditWriter writer;
    @SpyBean AdminActionGuard guard;
    private long adminId, memberId, leaderId;
    private static final String REASON = "운영 정책 검토 완료";

    private <T> T tx(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    private AdminAuditWriter auditTarget() { return AopTestUtils.getUltimateTargetObject(writer); }
    private AdminActionGuard guardTarget() { return AopTestUtils.getUltimateTargetObject(guard); }
    @BeforeEach void seed() {
        reset(auditTarget(), guardTarget());
        tx(() -> {
            Member admin = member("관리자"); admin.changeRole(UserRole.ADMIN); em.persist(admin);
            Member target = member("대상 회원"); em.persist(target);
            Member leader = member("다른 리더"); em.persist(leader);
            em.flush(); adminId = admin.getId(); memberId = target.getId(); leaderId = leader.getId();
            return null;
        });
    }
    private Member member(String name) { return Member.createForTest(UUID.randomUUID() + "@example.invalid", name); }
    private Project project(Member creator, ProjectVisibility visibility, Recruitment status) {
        Project p = Project.builder().creator(creator).name("관리 대상 프로젝트").description("소개")
                .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(status).recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();
        p.changeVisibility(visibility); em.persist(p); return p;
    }
    private JobPosition position() {
        var existing = em.createQuery("select p from JobPosition p order by p.id", JobPosition.class).setMaxResults(1).getResultList();
        if (!existing.isEmpty()) return existing.get(0);
        JobField field = JobField.of(JobFieldCode.BACKEND); em.persist(field);
        JobPosition p = JobPosition.of(field, JobPositionCode.values()[0]); em.persist(p); return p;
    }
    private RecruitmentState roster(Project project, Member member, JobPosition position, boolean closed) {
        RecruitmentState r = RecruitmentState.createRecruitmentState(position, 2);
        r.increaseCurrentCount(); if (closed) r.close(); project.addRecruitment(r); em.persist(r);
        ProjectMember pm = ProjectMember.createProjectMember(member, position,
                project.getCreator().getId().equals(member.getId()) ? ProjectMemberRole.LEADER : ProjectMemberRole.MEMBER);
        pm.assignProject(project); em.persist(pm); return r;
    }
    private long auditCount(long id) {
        return logs.logs(AdminQueryOptions.of(null, null, null, null), null, "MEMBER", "" + id).totalElements();
    }
    private void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test void roleChangeInvalidatesTokensPreservesSuspensionAndAuditsFromTo() {
        tx(() -> { em.find(Member.class, memberId).suspend(7, "기존 정지 사유", LocalDateTime.now()); return null; });
        assertThat(service.changeRole(adminId, memberId, UserRole.ADMIN, "  " + REASON + "  ").role()).isEqualTo("ADMIN");
        tx(() -> {
            Member m = em.find(Member.class, memberId);
            assertThat(m.getAuthVersion()).isEqualTo(2);
            assertThat(m.isSuspended()).isTrue(); assertThat(m.isDeleted()).isFalse(); return null;
        });
        var page = logs.logs(AdminQueryOptions.of(null,null,null,null), "MEMBER_ROLE_CHANGE", "MEMBER", "" + memberId);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).metadata()).containsEntry("from", "USER").containsEntry("to", "ADMIN");
        assertThat(page.content().get(0).reason()).isEqualTo(REASON);
    }

    @Test void visibleCreatorCannotBeWithdrawnEvenWithoutRoster() {
        tx(() -> { project(em.find(Member.class, memberId), ProjectVisibility.VISIBLE, Recruitment.RECRUITING); return null; });
        code(() -> service.withdraw(adminId, memberId, REASON), ErrorCode.MEMBER_HAS_LED_PROJECTS);
        tx(() -> { assertThat(em.find(Member.class, memberId).isDeleted()).isFalse(); return null; });
        assertThat(auditCount(memberId)).isZero();
    }

    @Test void hiddenCreatorWithdrawalKeepsCreatorHistoryClearsSuspensionAndRoleUnchanged() {
        long projectId = tx(() -> {
            Member m = em.find(Member.class, memberId); m.changeRole(UserRole.ADMIN); m.suspend(null, REASON, LocalDateTime.now());
            Project p = project(m, ProjectVisibility.HIDDEN, Recruitment.CLOSED);
            JobPosition pos = position();
            roster(p, m, pos, true);
            // One recruited teammate shares the leader's position; currentCount excludes the leader.
            ProjectMember teammate = ProjectMember.createProjectMember(em.find(Member.class, leaderId), pos, ProjectMemberRole.MEMBER);
            teammate.assignProject(p); em.persist(teammate);
            em.flush(); return p.getId();
        });
        assertThat(service.withdraw(adminId, memberId, REASON).status()).isEqualTo("WITHDRAWN");
        tx(() -> {
            Member m = em.find(Member.class, memberId);
            assertThat(m.isAdminWithdrawn()).isTrue(); assertThat(m.isDeleted()).isTrue();
            assertThat(m.getRole()).isEqualTo(UserRole.ADMIN); assertThat(m.getAuthVersion()).isEqualTo(3);
            assertThat(m.getIsParticipating()).isFalse(); assertThat(m.isSuspended()).isFalse();
            assertThat(m.getSuspendedUntil()).isNull(); assertThat(m.getSuspendReason()).isNull();
            Project p = em.find(Project.class, projectId);
            assertThat(p.getCreator().getId()).isEqualTo(memberId);
            assertThat(p.getMembers()).hasSize(1);
            assertThat(p.getMembers().get(0).getMember().getId()).isEqualTo(leaderId);
            assertThat(p.getRecruitments().get(0).getCurrentCount()).isEqualTo(1);
            assertThat(p.getRecruitmentStatus()).isEqualTo(Recruitment.CLOSED);
            return null;
        });
        assertThat(auditCount(memberId)).isEqualTo(1);
    }

    @Test void withdrawalCleansAllVisibilityRostersAndOnlyPendingApplicationsWithoutReopening() {
        List<Long> projectIds = tx(() -> {
            List<Long> ids = new ArrayList<>();
            Member leader = em.find(Member.class, leaderId), member = em.find(Member.class, memberId);
            JobPosition pos = position();
            for (ProjectVisibility visibility : ProjectVisibility.values()) {
                for (Recruitment status : Recruitment.values()) {
                    Project p = project(leader, visibility, status);
                    roster(p, member, pos, true);
                    for (ApplicationStatus applicationStatus : List.of(ApplicationStatus.PENDING, ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED)) {
                        ProjectApplication a = ProjectApplication.create(p, member, pos, "기록 보존");
                        a.updateStatus(applicationStatus); em.persist(a);
                    }
                    em.flush(); ids.add(p.getId());
                }
            }
            return ids;
        });
        service.withdraw(adminId, memberId, REASON);
        tx(() -> {
            assertThat(em.createQuery("select pm from ProjectMember pm where pm.member.id = :id", ProjectMember.class)
                    .setParameter("id", memberId).getResultList()).isEmpty();
            var applications = em.createQuery("select a from ProjectApplication a where a.applicant.id = :id", ProjectApplication.class)
                    .setParameter("id", memberId).getResultList();
            assertThat(applications).hasSize(projectIds.size() * 3);
            assertThat(applications).filteredOn(a -> a.getStatus() == ApplicationStatus.CANCELLED).hasSize(projectIds.size());
            assertThat(applications).filteredOn(a -> a.getStatus() == ApplicationStatus.ACCEPTED).hasSize(projectIds.size());
            assertThat(applications).filteredOn(a -> a.getStatus() == ApplicationStatus.REJECTED).hasSize(projectIds.size());
            for (long id : projectIds) {
                Project p = em.find(Project.class, id); RecruitmentState r = p.getRecruitments().get(0);
                assertThat(r.getCurrentCount()).isZero(); assertThat(r.isClosed()).isTrue();
                assertThat(p.getRecruitmentStatus()).isIn(Recruitment.CLOSED, Recruitment.SUSPENDED);
            }
            return null;
        });
    }

    @Test void vacancyDoesNotReopenClosedOrSuspendedProjectsButRecruitingOpenPositionsStayOpen() {
        Map<Recruitment, Long> ids = tx(() -> {
            Map<Recruitment, Long> result = new EnumMap<>(Recruitment.class);
            for (Recruitment status : Recruitment.values()) {
                Project p = project(em.find(Member.class, leaderId), ProjectVisibility.HIDDEN, status);
                roster(p, em.find(Member.class, memberId), position(), false);
                em.flush(); result.put(status, p.getId());
            }
            return result;
        });
        service.withdraw(adminId, memberId, REASON);
        tx(() -> {
            ids.forEach((status, id) -> {
                Project p = em.find(Project.class, id);
                assertThat(p.getRecruitmentStatus()).isEqualTo(status);
                assertThat(p.getRecruitments().get(0).getCurrentCount()).isZero();
                assertThat(p.getRecruitments().get(0).isClosed()).isFalse();
            });
            return null;
        });
        var page = logs.logs(AdminQueryOptions.of(null,null,null,null), "MEMBER_WITHDRAW", "MEMBER", "" + memberId);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).metadata()).containsEntry("removedMemberships", ids.size());
    }

    @Test void auditFailureRollsBackRoleAndWithdrawalIncludingRosterAndApplications() {
        long projectId = tx(() -> {
            Project p = project(em.find(Member.class, leaderId), ProjectVisibility.DELETED, Recruitment.CLOSED);
            JobPosition pos = position(); Member member = em.find(Member.class, memberId);
            roster(p, member, pos, false);
            em.persist(ProjectApplication.create(p, member, pos, "지원 기록")); em.flush(); return p.getId();
        });
        doThrow(new IllegalStateException("audit unavailable")).when(auditTarget()).append(any());
        assertThatThrownBy(() -> service.changeRole(adminId, memberId, UserRole.ADMIN, REASON)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.withdraw(adminId, memberId, REASON)).isInstanceOf(IllegalStateException.class);
        tx(() -> {
            Member m = em.find(Member.class, memberId);
            assertThat(m.getRole()).isEqualTo(UserRole.USER); assertThat(m.getAuthVersion()).isZero();
            assertThat(m.isDeleted()).isFalse(); assertThat(m.isAdminWithdrawn()).isFalse();
            Project p = em.find(Project.class, projectId); assertThat(p.getMembers()).hasSize(1);
            assertThat(p.getRecruitments().get(0).getCurrentCount()).isEqualTo(1);
            assertThat(em.createQuery("select a from ProjectApplication a where a.applicant.id = :id", ProjectApplication.class)
                    .setParameter("id", memberId).getSingleResult().getStatus()).isEqualTo(ApplicationStatus.PENDING);
            return null;
        });
        assertThat(auditCount(memberId)).isZero();
    }

    @Test void rejectsSelfSameStateMissingTargetInvalidReasonAndWithdrawnTargets() {
        code(() -> service.changeRole(adminId, adminId, UserRole.USER, REASON), ErrorCode.ADMIN_SELF_ACTION);
        code(() -> service.withdraw(adminId, adminId, REASON), ErrorCode.ADMIN_SELF_ACTION);
        code(() -> service.changeRole(adminId, memberId, UserRole.USER, REASON), ErrorCode.ADMIN_INVALID_STATE);
        code(() -> service.changeRole(adminId, memberId, null, REASON), ErrorCode.ADMIN_INVALID_REQUEST);
        code(() -> service.withdraw(adminId, memberId, "bad"), ErrorCode.ADMIN_INVALID_REQUEST);
        code(() -> service.withdraw(memberId, adminId, REASON), ErrorCode.ADMIN_FORBIDDEN);
        code(() -> service.withdraw(adminId, Long.MAX_VALUE, REASON), ErrorCode.ADMIN_TARGET_NOT_FOUND);
        service.withdraw(adminId, memberId, REASON);
        code(() -> service.withdraw(adminId, memberId, REASON), ErrorCode.ADMIN_INVALID_STATE);
        code(() -> service.changeRole(adminId, memberId, UserRole.ADMIN, REASON), ErrorCode.ADMIN_INVALID_STATE);
    }

    @Test void reservedInternalIdentityCannotBeWithdrawnOrPromotedBecauseStartupRunnerRequiresActiveUser() {
        long reserved = tx(() -> {
            var existing = em.createQuery("select m from Member m where m.email = :email", Member.class)
                    .setParameter("email", TestAccountService.EMAIL).getResultList();
            if (!existing.isEmpty()) return existing.get(0).getId();
            Member m = Member.createForTest(TestAccountService.EMAIL, "내부 테스트"); em.persist(m); em.flush(); return m.getId();
        });
        code(() -> service.withdraw(adminId, reserved, REASON), ErrorCode.ADMIN_INVALID_STATE);
        code(() -> service.changeRole(adminId, reserved, UserRole.ADMIN, REASON), ErrorCode.ADMIN_INVALID_STATE);
        assertThat(auditCount(reserved)).isZero();
    }

    @Test void lastActiveAdminGuardIsCheckedBeforeDemotionOrWithdrawal() {
        tx(() -> { em.find(Member.class, memberId).changeRole(UserRole.ADMIN); return null; });
        // Valid actors normally imply two active admins; stub the guarded count to exercise the defensive invariant.
        doReturn(1L).when(guardTarget()).activeAdminCount(any());
        code(() -> service.changeRole(adminId, memberId, UserRole.USER, REASON), ErrorCode.ADMIN_LAST_ADMIN);
        code(() -> service.withdraw(adminId, memberId, REASON), ErrorCode.ADMIN_LAST_ADMIN);
        tx(() -> { assertThat(em.find(Member.class, memberId).getRole()).isEqualTo(UserRole.ADMIN); return null; });
    }

    @Test void reciprocalConcurrentDemotionsCannotLeaveZeroAdmins() throws Exception {
        tx(() -> { em.find(Member.class, memberId).changeRole(UserRole.ADMIN); return null; });
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = pool.submit(() -> { start.await(); return demote(adminId, memberId); });
            Future<String> second = pool.submit(() -> { start.await(); return demote(memberId, adminId); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ok", ErrorCode.ADMIN_FORBIDDEN.getCode());
            tx(() -> {
                assertThat(List.of(em.find(Member.class, adminId), em.find(Member.class, memberId)))
                        .filteredOn(m -> m.getRole() == UserRole.ADMIN && !m.isDeleted() && !m.isSuspended()).hasSize(1);
                return null;
            });
        } finally { pool.shutdownNow(); }
    }
    private String demote(long actor, long target) {
        try { service.changeRole(actor, target, UserRole.USER, REASON); return "ok"; }
        catch (CustomException e) { return e.getErrorCode().getCode(); }
    }
}
