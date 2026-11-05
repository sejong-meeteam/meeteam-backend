package com.wardk.meeteam_backend.adminadmissionunit;

import com.wardk.meeteam_backend.domain.admin.*;
import com.wardk.meeteam_backend.domain.application.entity.*;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationService;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationServiceImpl;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.job.entity.*;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberServiceImpl;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.repository.RecruitmentStateRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.application.dto.request.ApplicationDecisionRequest;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2 transactions and row locks; repository mocks only bridge to the shared EntityManager. */
@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = AdmissionWithdrawalPersistenceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdmissionWithdrawalPersistenceTest {
    @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
    @Import({AdminMemberActionService.class, AdminActionGuard.class, AdminAuditWriter.class,
            AdminQueryRepository.class, AdminResponseMapper.class, ProjectApplicationServiceImpl.class,
            ProjectMemberServiceImpl.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired ProjectApplicationService admission;
    @Autowired AdminMemberActionService withdrawal;
    @MockBean MemberRepository members;
    @MockBean ProjectRepository projects;
    @MockBean ProjectApplicationRepository applications;
    @MockBean ProjectMemberRepository memberships;
    @MockBean JobPositionRepository positions;
    @MockBean RecruitmentStateRepository recruitments;
    @MockBean NotificationSaveService notifications;
    @MockBean S3ImageUrlResolver images;
    private long adminId, memberId, applicationId;
    private String leaderEmail;
    private <T> T tx(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(manager).execute(status -> work.get());
    }
    private Member member() { return Member.createForTest(UUID.randomUUID() + "@example.invalid", "회원"); }
    @BeforeEach void seedAndBridgeRepositories() {
        tx(() -> {
            Member admin = member(); admin.changeRole(UserRole.ADMIN); em.persist(admin);
            Member applicant = member(), leader = member(); em.persist(applicant); em.persist(leader);
            var existingPositions = em.createQuery("select p from JobPosition p order by p.id", JobPosition.class)
                    .setMaxResults(1).getResultList();
            JobPosition position;
            if (existingPositions.isEmpty()) {
                JobField field = JobField.of(JobFieldCode.BACKEND); em.persist(field);
                position = JobPosition.of(field, JobPositionCode.values()[0]); em.persist(position);
            } else {
                position = existingPositions.get(0);
            }
            Project project = Project.builder().creator(leader).name("동시성 검증 프로젝트").description("소개")
                    .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                    .recruitmentStatus(Recruitment.RECRUITING).recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();
            em.persist(project);
            RecruitmentState recruitment = RecruitmentState.createRecruitmentState(position, 2);
            project.addRecruitment(recruitment); em.persist(recruitment);
            ProjectApplication application = ProjectApplication.create(project, applicant, position, "지원"); em.persist(application);
            em.flush(); adminId = admin.getId(); memberId = applicant.getId(); applicationId = application.getId(); leaderEmail = leader.getEmail();
            return null;
        });
        when(members.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(
                em.find(Member.class, call.getArgument(0))));
        when(projects.findActiveById(anyLong())).thenAnswer(call -> Optional.ofNullable(
                em.find(Project.class, call.getArgument(0))));
        when(applications.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(em.find(ProjectApplication.class, call.getArgument(0))));
        when(memberships.save(any(ProjectMember.class))).thenAnswer(call -> { em.persist(call.getArgument(0)); return call.getArgument(0); });
    }
    private String approve() {
        try { admission.decide(applicationId, new ApplicationDecisionRequest(ApplicationStatus.ACCEPTED), leaderEmail); return "accepted"; }
        catch (CustomException e) { return e.getErrorCode().getCode(); }
    }
    private void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out awaiting race checkpoint");
    }
    private void assertWithdrawn(ApplicationStatus expectedHistory) {
        tx(() -> {
            assertThat(em.find(Member.class, memberId).isDeleted()).isTrue();
            assertThat(em.find(ProjectApplication.class, applicationId).getStatus()).isEqualTo(expectedHistory);
            assertThat(em.createQuery("select pm from ProjectMember pm where pm.member.id = :id", ProjectMember.class)
                    .setParameter("id", memberId).getResultList()).isEmpty();
            return null;
        });
    }

    @Test void withdrawalCommittedAfterApprovalPreReadPreventsStalePendingAcceptance() throws Exception {
        CountDownLatch preRead = new CountDownLatch(1), resume = new CountDownLatch(1);
        when(applications.findById(applicationId)).thenAnswer(call -> {
            ProjectApplication a = em.find(ProjectApplication.class, applicationId);
            // Initialize the same stale entities used by ordinary authorization before the withdrawal commits.
            a.getApplicant().getEmail(); a.getProject().getCreator().getEmail();
            preRead.countDown(); await(resume); return Optional.of(a);
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> approval = pool.submit(this::approve);
            await(preRead);
            withdrawal.withdraw(adminId, memberId, "동시성 검증을 위한 운영 조치");
            resume.countDown();
            assertThat(approval.get(15, TimeUnit.SECONDS)).isEqualTo(ErrorCode.MEMBER_WITHDRAWN.getCode());
            assertWithdrawn(ApplicationStatus.CANCELLED);
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test void approvalHoldingMemberLockCommitsFirstThenWithdrawalRemovesNewMembership() throws Exception {
        CountDownLatch memberLocked = new CountDownLatch(1), resume = new CountDownLatch(1), withdrawalStarted = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            Member m = em.find(Member.class, memberId);
            em.refresh(m, LockModeType.PESSIMISTIC_WRITE);
            if (first.getAndSet(false)) { memberLocked.countDown(); await(resume); }
            return Optional.of(m);
        }).when(members).findById(memberId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> approval = pool.submit(this::approve); await(memberLocked);
            Future<?> removal = pool.submit(() -> {
                withdrawalStarted.countDown(); withdrawal.withdraw(adminId, memberId, "동시성 검증을 위한 운영 조치");
            });
            await(withdrawalStarted); resume.countDown();
            assertThat(approval.get(15, TimeUnit.SECONDS)).isEqualTo("accepted");
            removal.get(15, TimeUnit.SECONDS);
            assertWithdrawn(ApplicationStatus.ACCEPTED);
        } finally { resume.countDown(); pool.shutdownNow(); }
    }
}
