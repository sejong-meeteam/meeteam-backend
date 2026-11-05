package com.wardk.meeteam_backend.adminprojectactionunit;

import com.wardk.meeteam_backend.domain.admin.*;
import com.wardk.meeteam_backend.domain.application.entity.*;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.job.entity.*;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.projectmember.entity.*;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
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
import org.springframework.test.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Embedded database only; no application bootstrap, external services, or GitHub calls. */
@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = AdminProjectActionPersistenceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminProjectActionPersistenceTest {
    @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
    @Import({AdminProjectActionService.class, AdminActionGuard.class, AdminAuditWriter.class,
            AdminQueryRepository.class, AdminResponseMapper.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
    }
    @Autowired EntityManager em;
    @Autowired AdminProjectActionService service;
    @Autowired PlatformTransactionManager manager;
    @MockBean S3ImageUrlResolver images;
    @SpyBean AdminAuditWriter writer;
    private long adminId, leaderId, memberId, projectId, applicationId;
    private static final String REASON = "운영 정책 위반 확인";

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    @BeforeEach void seed() {
        reset((AdminAuditWriter) AopTestUtils.getUltimateTargetObject(writer));
        tx(() -> {
            Member admin = member("관리자"), leader = member("리더"), target = member("팀원"), applicant = member("지원자");
            ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
            // Reuse reference codes across committed fixtures: both columns are globally unique.
            JobPosition position = em.createQuery("select p from JobPosition p where p.code = :code", JobPosition.class)
                    .setParameter("code", JobPositionCode.JAVA_SPRING).getResultStream().findFirst().orElseGet(() -> {
                        JobField field = JobField.of(JobFieldCode.BACKEND); em.persist(field);
                        JobPosition created = JobPosition.of(field, JobPositionCode.JAVA_SPRING); em.persist(created); return created;
                    });
            Project project = Project.builder().creator(leader).name("관리 대상").description("소개")
                    .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                    .recruitmentStatus(Recruitment.RECRUITING)
                    .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();
            RecruitmentState state = RecruitmentState.createRecruitmentState(position, 2);
            state.updateCurrentCount(1); project.addRecruitment(state);
            project.joinMember(ProjectMember.createProjectMember(leader, position, ProjectMemberRole.LEADER));
            project.joinMember(ProjectMember.createProjectMember(target, position, ProjectMemberRole.MEMBER));
            em.persist(project);
            ProjectApplication application = ProjectApplication.create(project, applicant, position, "지원 동기");
            em.persist(application); em.flush();
            adminId = admin.getId(); leaderId = leader.getId(); memberId = target.getId();
            projectId = project.getId(); applicationId = application.getId();
            return null;
        });
    }
    private Member member(String name) {
        Member member = Member.createForTest(UUID.randomUUID() + "@example.invalid", name);
        em.persist(member); return member;
    }
    private Project project() { return em.find(Project.class, projectId); }
    private RecruitmentState state() { return project().getRecruitments().get(0); }
    private List<AdminAuditLog> audits() {
        return em.createQuery("select a from AdminAuditLog a where a.targetId = :id order by a.id", AdminAuditLog.class)
                .setParameter("id", projectId).getResultList();
    }
    @Test void forceClosePreservesHiddenVisibilityApplicationsAndCountsAndCommitsAudit() {
        tx(() -> { project().changeVisibility(ProjectVisibility.HIDDEN); return null; });
        var result = service.close(adminId, projectId, "  " + REASON + "  ");
        assertThat(result.recruitmentStatus()).isEqualTo("CLOSED");
        assertThat(result.visibility()).isEqualTo("HIDDEN");
        assertThat(result.currentMembers()).isEqualTo(2);
        assertThat(result.pendingApplicationCount()).isEqualTo(1);
        tx(() -> {
            assertThat(state().isClosed()).isTrue();
            assertThat(state().getCurrentCount()).isEqualTo(1);
            assertThat(em.find(ProjectApplication.class, applicationId).isPending()).isTrue();
            assertThat(audits()).singleElement().satisfies(a -> {
                assertThat(a.getAction()).isEqualTo(AuditAction.PROJECT_FORCE_CLOSE);
                assertThat(a.getReason()).isEqualTo(REASON);
                assertThat(a.getAdminId()).isEqualTo(adminId);
            });
            return null;
        });
        assertCode(() -> service.close(adminId, projectId, REASON), ErrorCode.ADMIN_INVALID_STATE);
    }
    @Test void forceCloseClosesEveryPositionIncludingPartlyFilledAndEmptyPositions() {
        tx(() -> {
            JobPosition other = em.createQuery("select p from JobPosition p where p.code = :code", JobPosition.class)
                    .setParameter("code", JobPositionCode.IOS).getResultStream().findFirst().orElseGet(() -> {
                        JobField field = em.createQuery("select f from JobField f where f.code = :code", JobField.class)
                                .setParameter("code", JobFieldCode.FRONTEND).getResultStream().findFirst().orElseGet(() -> {
                                    JobField created = JobField.of(JobFieldCode.FRONTEND); em.persist(created); return created;
                                });
                        JobPosition created = JobPosition.of(field, JobPositionCode.IOS); em.persist(created); return created;
                    });
            project().addRecruitment(RecruitmentState.createRecruitmentState(other, 3));
            return null;
        });
        service.close(adminId, projectId, REASON);
        tx(() -> {
            assertThat(project().getRecruitments()).hasSize(2).allMatch(RecruitmentState::isClosed);
            project().updateRecruitmentStatusBasedOnPositions();
            assertThat(project().isClosed()).isTrue(); return null;
        });
    }
    @Test void expelOnDeletedForceClosedProjectDoesNotReopenAndPreservesPending() {
        tx(() -> { project().changeVisibility(ProjectVisibility.DELETED); return null; });
        service.close(adminId, projectId, REASON);
        var result = service.expel(adminId, projectId, memberId, REASON);
        assertThat(result.currentMembers()).isEqualTo(1);
        assertThat(result.recruitmentStatus()).isEqualTo("CLOSED");
        assertThat(result.visibility()).isEqualTo("DELETED");
        assertThat(result.pendingApplicationCount()).isEqualTo(1);
        tx(() -> {
            assertThat(state().getCurrentCount()).isZero();
            assertThat(state().isClosed()).isTrue();
            assertThat(project().getMembers()).hasSize(1);
            assertThat(em.find(ProjectApplication.class, applicationId).isPending()).isTrue();
            assertThat(audits()).hasSize(2);
            assertThat(audits().get(1).getAction()).isEqualTo(AuditAction.PROJECT_MEMBER_EXPEL);
            assertThat(audits().get(1).getMetadata()).containsEntry("memberName", "팀원");
            assertThat(((Number) audits().get(1).getMetadata().get("memberId")).longValue()).isEqualTo(memberId);
            return null;
        });
        assertCode(() -> service.expel(adminId, projectId, memberId, REASON), ErrorCode.ADMIN_TARGET_NOT_FOUND);
    }
    @Test void expelUpdatesAlreadyLoadedCollectionAndAutomaticallyFullPositionReopens() {
        tx(() -> {
            state().updateRecruitmentCount(1);
            project().updateRecruitmentStatusBasedOnPositions();
            assertThat(project().isClosed()).isTrue();
            return null;
        });
        tx(() -> {
            Project managed = project(); assertThat(managed.getMembers()).hasSize(2);
            service.expel(adminId, projectId, memberId, REASON);
            assertThat(managed.getMembers()).hasSize(1);
            assertThat(state().getCurrentCount()).isZero();
            assertThat(managed.isRecruiting()).isTrue();
            return null;
        });
    }
    @Test void manuallyClosedPositionRemainsClosedAfterExpelAndSuspendedProjectStaysSuspended() {
        tx(() -> { state().close(); project().updateRecruitmentStatusBasedOnPositions(); return null; });
        assertThat(service.expel(adminId, projectId, memberId, REASON).recruitmentStatus()).isEqualTo("CLOSED");
        tx(() -> {
            Project p = project(); p.suspend();
            p.updateRecruitmentStatusBasedOnPositions(); assertThat(p.isSuspended()).isTrue(); return null;
        });
        assertThat(service.close(adminId, projectId, REASON).recruitmentStatus()).isEqualTo("CLOSED");
    }
    @Test void missingLeaderAssociationCannotBypassCreatorProtection() {
        tx(() -> {
            Project p = project();
            var leader = p.getMembers().stream().filter(m -> m.getMember().getId().equals(leaderId)).findFirst().orElseThrow();
            p.getMembers().remove(leader); em.remove(leader); return null;
        });
        assertCode(() -> service.expel(adminId, projectId, leaderId, REASON), ErrorCode.PROJECT_LEADER_EXPEL);
        assertCode(() -> service.expel(adminId, projectId, Long.MAX_VALUE, REASON), ErrorCode.ADMIN_TARGET_NOT_FOUND);
    }
    @Test void invalidReasonsActorsAndMissingProjectsHaveNoSideEffects() {
        assertCode(() -> service.close(adminId, projectId, "짧음"), ErrorCode.ADMIN_INVALID_REQUEST);
        assertCode(() -> service.expel(adminId, projectId, memberId, " "), ErrorCode.ADMIN_INVALID_REQUEST);
        assertCode(() -> service.close(memberId, projectId, REASON), ErrorCode.ADMIN_FORBIDDEN);
        assertCode(() -> service.expel(memberId, projectId, memberId, REASON), ErrorCode.ADMIN_FORBIDDEN);
        assertCode(() -> service.close(adminId, Long.MAX_VALUE, REASON), ErrorCode.ADMIN_TARGET_NOT_FOUND);
        assertCode(() -> service.expel(adminId, Long.MAX_VALUE, memberId, REASON), ErrorCode.ADMIN_TARGET_NOT_FOUND);
        tx(() -> { assertThat(audits()).isEmpty(); assertThat(project().getMembers()).hasSize(2); return null; });
    }
    @Test void auditFailureRollsBackClosureAndEveryPositionFlag() {
        failAudit();
        assertThatThrownBy(() -> service.close(adminId, projectId, REASON)).isInstanceOf(IllegalStateException.class);
        tx(() -> {
            assertThat(project().isRecruiting()).isTrue(); assertThat(state().isClosed()).isFalse();
            assertThat(audits()).isEmpty(); return null;
        });
    }
    @Test void auditFailureRollsBackRosterCountsAndAutomaticStatus() {
        tx(() -> { state().updateRecruitmentCount(1); project().updateRecruitmentStatusBasedOnPositions(); return null; });
        failAudit();
        assertThatThrownBy(() -> service.expel(adminId, projectId, memberId, REASON)).isInstanceOf(IllegalStateException.class);
        tx(() -> {
            assertThat(project().getMembers()).hasSize(2); assertThat(state().getCurrentCount()).isEqualTo(1);
            assertThat(project().isClosed()).isTrue(); assertThat(audits()).isEmpty(); return null;
        });
    }
    private void failAudit() {
        doThrow(new IllegalStateException("simulated audit failure"))
                .when((AdminAuditWriter) AopTestUtils.getUltimateTargetObject(writer)).append(any());
    }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
