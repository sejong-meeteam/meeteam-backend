package com.wardk.meeteam_backend.proposalunit;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.notification.entity.Notification;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.repository.NotificationRepository;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.domain.proposal.entity.ProposalStatus;
import com.wardk.meeteam_backend.domain.proposal.repository.ProjectProposalRepository;
import com.wardk.meeteam_backend.domain.proposal.service.ProjectProposalService;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.notification.dto.response.NotificationResponse;
import com.wardk.meeteam_backend.web.proposal.dto.request.ProjectProposalRequest;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProjectProposalResponse;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProposableProjectResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import com.wardk.meeteam_backend.global.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real H2 repositories, auditing and notification persistence; only the async SSE listener is outside the slice. */
@DataJpaTest(excludeAutoConfiguration = JpaRepositoriesAutoConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:project-proposal;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "app.proposal.daily-limit=20"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ProjectProposalServiceTest.Config.class)
@RecordApplicationEvents
class ProjectProposalServiceTest {

    // @Configuration을 붙이지 않는다: 붙이면 다른 @SpringBootTest의 컴포넌트 스캔에 잡혀
    // 제한된 @EnableJpaRepositories가 전체 컨텍스트의 저장소 자동 구성을 덮어쓴다.
    @EntityScan("com.wardk.meeteam_backend.domain")
    @EnableJpaRepositories(basePackageClasses = {ProjectRepository.class, MemberRepository.class,
            ProjectMemberRepository.class, ProjectProposalRepository.class, NotificationRepository.class})
    @Import({JpaAuditingConfig.class, ProjectProposalService.class, NotificationSaveService.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
        @Bean JPAQueryFactory queryFactory(EntityManager em) { return new JPAQueryFactory(em); }
    }

    @Autowired EntityManager em;
    @Autowired ProjectProposalService service;
    @Autowired ProjectProposalRepository proposals;
    @Autowired NotificationRepository notifications;
    @Autowired ApplicationEvents events;

    Member leader, target;
    JobPosition frontend, backend;
    Project project;

    @BeforeEach
    void seed() {
        JobField frontField = persist(JobField.of(JobFieldCode.FRONTEND));
        JobField backField = persist(JobField.of(JobFieldCode.BACKEND));
        frontend = persist(JobPosition.of(frontField, JobPositionCode.WEB_FRONTEND));
        backend = persist(JobPosition.of(backField, JobPositionCode.JAVA_SPRING));
        leader = sejongMember("리더");
        target = sejongMember("홍길동");
        project = recruitingProject("캠퍼스 메이커스", leader, LocalDate.now().plusDays(7));
        em.flush();
    }

    @Nested
    class Propose {

        @Test
        void savesPendingProposalWithTrimmedMessageAndNotifiesReceiverInSameTransaction() {
            ProjectProposalResponse response = service.propose(project.getId(), leader.getId(),
                    request(target, frontend, "  기술 스택이 잘 맞아서 제안드려요.  "));

            assertThat(response.status()).isEqualTo(ProposalStatus.PENDING);
            assertThat(response.projectId()).isEqualTo(project.getId());
            assertThat(response.memberId()).isEqualTo(target.getId());
            assertThat(response.jobPositionId()).isEqualTo(frontend.getId());
            assertThat(response.message()).isEqualTo("기술 스택이 잘 맞아서 제안드려요.");
            assertThat(response.createdAt().getOffset().getId()).isEqualTo("+09:00");

            ProjectProposal saved = proposals.findById(response.proposalId()).orElseThrow();
            assertThat(saved.getProposer().getId()).isEqualTo(leader.getId());
            assertThat(saved.getReceiver().getId()).isEqualTo(target.getId());

            List<Notification> received = notifications.findByMemberId(target.getId(), PageRequest.of(0, 10)).getContent();
            assertThat(received).singleElement().satisfies(notification -> {
                assertThat(notification.getType()).isEqualTo(NotificationType.PROJECT_PROPOSAL_RECEIVED);
                assertThat(notification.getActorId()).isEqualTo(leader.getId());
                assertThat(notification.getProposal().getId()).isEqualTo(response.proposalId());
            });
            assertThat(events.stream(NotificationEvent.class)).singleElement().satisfies(event -> {
                assertThat(event.getType()).isEqualTo(NotificationType.PROJECT_PROPOSAL_RECEIVED);
                assertThat(event.getReceiverId()).isEqualTo(target.getId());
                assertThat(event.getActorId()).isEqualTo(leader.getId());
                assertThat(event.getProposalId()).isEqualTo(response.proposalId());
            });
        }

        @Test
        void notificationListCarriesProposalPayloadForTheReceiver() {
            service.propose(project.getId(), leader.getId(), request(target, frontend, "함께해요"));
            em.flush();
            em.clear();

            Notification notification = notifications.findByMemberId(target.getId(), PageRequest.of(0, 10)).getContent().get(0);
            var payload = new NotificationResponse(notification).getPayload();

            assertThat(payload.getProjectName()).isEqualTo("캠퍼스 메이커스");
            assertThat(payload.getProposerId()).isEqualTo(leader.getId());
            assertThat(payload.getProposerName()).isEqualTo("리더");
            assertThat(payload.getJobFieldName()).isEqualTo("프론트");
            assertThat(payload.getJobPositionName()).isEqualTo("웹 프론트엔드");
            assertThat(payload.getProposalMessage()).isEqualTo("함께해요");
        }

        @Test
        void blankMessageIsStoredEmptyAndOmittedFromNotification() {
            ProjectProposalResponse response = service.propose(project.getId(), leader.getId(), request(target, frontend, "   "));
            em.flush();
            em.clear();

            assertThat(response.message()).isEmpty();
            Notification notification = notifications.findByMemberId(target.getId(), PageRequest.of(0, 10)).getContent().get(0);
            assertThat(new NotificationResponse(notification).getPayload().getProposalMessage()).isNull();
        }

        @Test
        void missingOrHiddenProjectIsNotFound() {
            assertError(() -> service.propose(999_999L, leader.getId(), request(target, frontend, "")), ErrorCode.PROJECT_NOT_FOUND);

            project.changeVisibility(ProjectVisibility.HIDDEN);
            assertError(() -> propose(target, frontend), ErrorCode.PROJECT_NOT_FOUND);
        }

        @Test
        void onlyTheProjectLeaderCanPropose() {
            Member teammate = sejongMember("팀원");

            assertError(() -> service.propose(project.getId(), teammate.getId(), request(target, frontend, "")),
                    ErrorCode.PROPOSAL_NOT_LEADER);
        }

        @Test
        void suspendedRecruitmentIsNotRecruiting() {
            project.suspend();

            assertError(() -> propose(target, frontend), ErrorCode.PROPOSAL_PROJECT_NOT_RECRUITING);
        }

        @Test
        void passedDeadlineIsNotRecruiting() {
            Project expired = recruitingProject("마감 지난 프로젝트", leader, LocalDate.now().minusDays(1));

            assertError(() -> service.propose(expired.getId(), leader.getId(), request(target, frontend, "")),
                    ErrorCode.PROPOSAL_PROJECT_NOT_RECRUITING);
        }

        @Test
        void projectWithEveryPositionFullIsNotRecruiting() {
            project.getRecruitments().forEach(RecruitmentState::close);

            assertError(() -> propose(target, frontend), ErrorCode.PROPOSAL_PROJECT_NOT_RECRUITING);
        }

        @Test
        void missingWithdrawnOrSuspendedReceiverIsNotFound() {
            assertError(() -> service.propose(project.getId(), leader.getId(), new ProjectProposalRequest(999_999L, frontend.getId(), "")),
                    ErrorCode.MEMBER_NOT_FOUND);

            Member withdrawn = sejongMember("탈퇴회원");
            withdrawn.withdraw();
            assertError(() -> propose(withdrawn, frontend), ErrorCode.MEMBER_NOT_FOUND);

            Member suspended = sejongMember("정지회원");
            suspended.suspend(7, "운영 정책 위반", LocalDateTime.now());
            assertError(() -> propose(suspended, frontend), ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        void cannotProposeToSelf() {
            assertError(() -> propose(leader, frontend), ErrorCode.PROPOSAL_SELF);
        }

        @Test
        void receiverMustShareTheLeadersVerifiedUniversity() {
            Member otherSchool = persist(Member.createForTest(UUID.randomUUID() + "@korea.ac.kr", "타학교"));

            assertError(() -> propose(otherSchool, frontend), ErrorCode.PROPOSAL_DIFFERENT_UNIVERSITY);
        }

        @Test
        void sejongAliasDomainsCountAsTheSameUniversity() {
            Member aliasDomain = persist(Member.createForTest(UUID.randomUUID() + "@sejong.ac.kr", "세종대"));

            assertThat(propose(aliasDomain, frontend).status()).isEqualTo(ProposalStatus.PENDING);
        }

        @Test
        void unverifiedAccountsNeverMatchEachOther() {
            Member unverifiedLeader = persist(Member.createForTest(UUID.randomUUID() + "@example.invalid", "미인증리더"));
            Member unverifiedTarget = persist(Member.createForTest(UUID.randomUUID() + "@example.invalid", "미인증회원"));
            Project unverifiedProject = recruitingProject("미인증 프로젝트", unverifiedLeader, null);

            assertError(() -> service.propose(unverifiedProject.getId(), unverifiedLeader.getId(), request(unverifiedTarget, frontend, "")),
                    ErrorCode.PROPOSAL_DIFFERENT_UNIVERSITY);
        }

        @Test
        void existingTeammateCannotBeProposedTo() {
            ProjectMember membership = ProjectMember.createProjectMember(target, backend, ProjectMemberRole.MEMBER);
            project.joinMember(membership);
            em.persist(membership);

            assertError(() -> propose(target, frontend), ErrorCode.PROPOSAL_ALREADY_MEMBER);
        }

        @Test
        void positionMustBeOpenInThisProject() {
            JobPosition notRecruited = persist(JobPosition.of(frontend.getJobField(), JobPositionCode.IOS));
            assertError(() -> propose(target, notRecruited), ErrorCode.PROPOSAL_POSITION_CLOSED);

            project.getRecruitments().stream()
                    .filter(recruitment -> recruitment.getJobPosition().equals(frontend))
                    .forEach(RecruitmentState::close);
            assertError(() -> propose(target, frontend), ErrorCode.PROPOSAL_POSITION_CLOSED);
        }

        @Test
        void secondPendingProposalForSameProjectAndMemberIsConflictNamingBoth() {
            propose(target, frontend);

            assertThatThrownBy(() -> propose(target, backend))
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_DUPLICATE);
                        assertThat(e.getErrorCode().getStatus().value()).isEqualTo(409);
                        assertThat(e.getMessage()).isEqualTo("이미 '캠퍼스 메이커스' 프로젝트로 홍길동님에게 보낸 제안이 있어요.");
                    });
        }

        @Test
        void duplicateMessageUsesTheReceiversPublicName() {
            target.updateNamePrivacy(true);
            propose(target, frontend);

            assertThatThrownBy(() -> propose(target, frontend))
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getMessage()).contains("홍**님").doesNotContain("홍길동"));
        }

        @Test
        void twentySendsWithin24HoursBlockTheNext() {
            seedProposalsFromLeader(20);

            assertThatThrownBy(() -> propose(target, frontend))
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_RATE_LIMITED);
                        assertThat(e.getErrorCode().getStatus().value()).isEqualTo(429);
                        assertThat(e.getMessage()).isEqualTo("제안은 하루 20건까지 보낼 수 있어요. 내일 다시 시도해 주세요.");
                    });
            assertThat(proposals.count()).isEqualTo(20);
        }

        @Test
        void sendsOlderThan24HoursDoNotCount() {
            seedProposalsFromLeader(20);
            em.createQuery("update ProjectProposal p set p.createdAt = :old")
                    .setParameter("old", LocalDateTime.now().minusHours(25)).executeUpdate();

            assertThat(propose(target, frontend).status()).isEqualTo(ProposalStatus.PENDING);
        }

        @Test
        void nineteenSendsStillAllowOneMore() {
            seedProposalsFromLeader(19);

            assertThat(propose(target, frontend).status()).isEqualTo(ProposalStatus.PENDING);
        }

        @Test
        void checksRunInContractOrder() {
            Member otherSchool = persist(Member.createForTest(UUID.randomUUID() + "@korea.ac.kr", "타학교"));
            Member teammate = sejongMember("팀원");
            project.suspend();

            // Not leader is reported before not-recruiting, and not-recruiting before receiver checks.
            assertError(() -> service.propose(project.getId(), teammate.getId(), request(otherSchool, frontend, "")),
                    ErrorCode.PROPOSAL_NOT_LEADER);
            assertError(() -> propose(otherSchool, frontend), ErrorCode.PROPOSAL_PROJECT_NOT_RECRUITING);
        }

        private void seedProposalsFromLeader(int count) {
            for (int i = 0; i < count; i++) {
                Member someone = sejongMember("회원" + i);
                em.persist(ProjectProposal.pending(project, leader, someone, frontend, ""));
            }
            em.flush();
        }
    }

    @Nested
    class ProposableProjects {

        @Test
        void listsMyRecruitingProjectsWithOpenPositionsAndPendingFlag() {
            Project teamProject = recruitingProject("이미 함께하는 프로젝트", leader, null);
            ProjectMember membership = ProjectMember.createProjectMember(target, frontend, ProjectMemberRole.MEMBER);
            teamProject.joinMember(membership);
            em.persist(membership);
            Project suspended = recruitingProject("모집 중단 프로젝트", leader, null);
            suspended.suspend();
            recruitingProject("마감 지난 프로젝트", leader, LocalDate.now().minusDays(1));
            Project hidden = recruitingProject("숨김 프로젝트", leader, null);
            hidden.changeVisibility(ProjectVisibility.HIDDEN);
            recruitingProject("남의 프로젝트", sejongMember("다른리더"), null);
            Project second = recruitingProject("두 번째 프로젝트", leader, null);
            project.getRecruitments().stream()
                    .filter(recruitment -> recruitment.getJobPosition().equals(backend))
                    .forEach(RecruitmentState::close);
            em.flush();

            propose(target, frontend);
            List<ProposableProjectResponse> result = service.getProposableProjects(leader.getId(), target.getId());

            assertThat(result).extracting(ProposableProjectResponse::projectName)
                    .containsExactlyInAnyOrder("캠퍼스 메이커스", "두 번째 프로젝트");
            ProposableProjectResponse main = result.stream().filter(p -> p.projectId().equals(project.getId())).findFirst().orElseThrow();
            assertThat(main.hasPendingProposal()).isTrue();
            assertThat(main.openPositions()).singleElement().satisfies(position -> {
                assertThat(position.jobPositionId()).isEqualTo(frontend.getId());
                assertThat(position.jobFieldName()).isEqualTo("프론트");
                assertThat(position.jobPositionName()).isEqualTo("웹 프론트엔드");
            });
            ProposableProjectResponse other = result.stream().filter(p -> p.projectId().equals(second.getId())).findFirst().orElseThrow();
            assertThat(other.hasPendingProposal()).isFalse();
            assertThat(other.openPositions()).hasSize(2);
        }

        @Test
        void leaderWithoutRecruitingProjectsGetsEmptyList() {
            Member newLeader = sejongMember("새리더");

            assertThat(service.getProposableProjects(newLeader.getId(), target.getId())).isEmpty();
        }

        @Test
        void rejectsMissingReceiverSelfAndOtherUniversity() {
            assertError(() -> service.getProposableProjects(leader.getId(), 999_999L), ErrorCode.MEMBER_NOT_FOUND);
            assertError(() -> service.getProposableProjects(leader.getId(), leader.getId()), ErrorCode.PROPOSAL_SELF);
            Member otherSchool = persist(Member.createForTest(UUID.randomUUID() + "@korea.ac.kr", "타학교"));
            assertError(() -> service.getProposableProjects(leader.getId(), otherSchool.getId()), ErrorCode.PROPOSAL_DIFFERENT_UNIVERSITY);
        }
    }

    private ProjectProposalResponse propose(Member receiver, JobPosition position) {
        return service.propose(project.getId(), leader.getId(), request(receiver, position, ""));
    }

    private static ProjectProposalRequest request(Member receiver, JobPosition position, String message) {
        return new ProjectProposalRequest(receiver.getId(), position.getId(), message);
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private Member sejongMember(String name) {
        return persist(Member.createForTest(UUID.randomUUID().toString().substring(0, 8) + "@sju.ac.kr", name));
    }

    private Project recruitingProject(String name, Member creator, LocalDate deadline) {
        Project created = Project.builder().creator(creator).name(name).description(name + " 설명")
                .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(deadline == null ? RecruitmentDeadlineType.RECRUITMENT_COMPLETED : RecruitmentDeadlineType.END_DATE)
                .endDate(deadline).build();
        em.persist(created);
        for (JobPosition position : List.of(frontend, backend)) {
            RecruitmentState recruitment = RecruitmentState.createRecruitmentState(position, 2);
            created.addRecruitment(recruitment);
            em.persist(recruitment);
        }
        return created;
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
