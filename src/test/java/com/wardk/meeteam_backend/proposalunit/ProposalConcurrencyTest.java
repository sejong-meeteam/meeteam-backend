package com.wardk.meeteam_backend.proposalunit;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.repository.NotificationRepository;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.domain.proposal.repository.ProjectProposalRepository;
import com.wardk.meeteam_backend.domain.proposal.service.ProjectProposalService;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.proposal.dto.request.ProjectProposalRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Real H2 transactions and row locks: two simultaneous sends for one pair must store a single proposal. */
@DataJpaTest(excludeAutoConfiguration = JpaRepositoriesAutoConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:proposal-race;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ProposalConcurrencyTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProposalConcurrencyTest {

    @Configuration
    @EntityScan("com.wardk.meeteam_backend.domain")
    @EnableJpaRepositories(basePackageClasses = {ProjectRepository.class, MemberRepository.class,
            ProjectMemberRepository.class, ProjectProposalRepository.class, NotificationRepository.class})
    @EnableJpaAuditing
    @Import({ProjectProposalService.class, NotificationSaveService.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
        @Bean JPAQueryFactory queryFactory(EntityManager em) { return new JPAQueryFactory(em); }
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired ProjectProposalService service;
    @Autowired ProjectProposalRepository proposals;

    @Test
    void simultaneousDuplicateSendsStoreOneProposal() throws Exception {
        long[] ids = new TransactionTemplate(manager).execute(status -> seed());
        long projectId = ids[0], leaderId = ids[1];
        var request = new ProjectProposalRequest(ids[2], ids[3], "");

        // Hold each sender at the duplicate check long enough for the other to catch up if nothing serializes them.
        CountDownLatch bothChecking = new CountDownLatch(2);
        ProjectProposalRepository paused = mock(ProjectProposalRepository.class, delegatesTo(proposals));
        doAnswer(call -> {
            bothChecking.countDown();
            bothChecking.await(500, TimeUnit.MILLISECONDS);
            return proposals.existsByProjectIdAndReceiverIdAndStatus(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        }).when(paused).existsByProjectIdAndReceiverIdAndStatus(any(), any(), any());
        Object target = AopTestUtils.getTargetObject(service);
        ReflectionTestUtils.setField(target, "proposalRepository", paused);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> send = () -> {
                try {
                    service.propose(projectId, leaderId, request);
                    return "sent";
                } catch (CustomException e) {
                    return e.getErrorCode().name();
                }
            };
            List<Future<String>> results = pool.invokeAll(List.of(send, send), 20, TimeUnit.SECONDS);

            assertThat(results.stream().map(this::get).toList())
                    .containsExactlyInAnyOrder("sent", ErrorCode.PROPOSAL_DUPLICATE.name());
            Long stored = new TransactionTemplate(manager).execute(status -> em.createQuery(
                    "select count(p) from ProjectProposal p where p.project.id = :id", Long.class)
                    .setParameter("id", projectId).getSingleResult());
            assertThat(stored).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            ReflectionTestUtils.setField(target, "proposalRepository", proposals);
        }
    }

    private long[] seed() {
        JobField field = JobField.of(JobFieldCode.FRONTEND);
        em.persist(field);
        JobPosition position = JobPosition.of(field, JobPositionCode.WEB_FRONTEND);
        em.persist(position);
        Member leader = Member.createForTest(UUID.randomUUID().toString().substring(0, 8) + "@sju.ac.kr", "리더");
        Member target = Member.createForTest(UUID.randomUUID().toString().substring(0, 8) + "@sju.ac.kr", "회원");
        em.persist(leader);
        em.persist(target);
        Project project = Project.builder().creator(leader).name("동시성 프로젝트").description("소개")
                .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.RECRUITMENT_COMPLETED).build();
        em.persist(project);
        RecruitmentState recruitment = RecruitmentState.createRecruitmentState(position, 2);
        project.addRecruitment(recruitment);
        em.persist(recruitment);
        return new long[]{project.getId(), leader.getId(), target.getId(), position.getId()};
    }

    private String get(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException | CancellationException e) {
            return "failed: " + e;
        }
    }
}
