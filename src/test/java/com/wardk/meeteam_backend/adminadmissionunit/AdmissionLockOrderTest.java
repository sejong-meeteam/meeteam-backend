package com.wardk.meeteam_backend.adminadmissionunit;

import com.wardk.meeteam_backend.domain.application.entity.*;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationServiceImpl;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.ProjectCommandServiceImpl;
import com.wardk.meeteam_backend.domain.project.service.ProjectManagementServiceImpl;
import com.wardk.meeteam_backend.web.projectmember.dto.request.WithdrawRequest;
import com.wardk.meeteam_backend.web.projectmember.dto.request.DeleteRequest;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberServiceImpl;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.application.dto.request.ApplicationDecisionRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionLockOrderTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository memberships = mock(ProjectMemberRepository.class);
    private final ProjectApplicationRepository applications = mock(ProjectApplicationRepository.class);
    private final EntityManager em = mock(EntityManager.class);

    private <T> T inject(T service) { ReflectionTestUtils.setField(service, "entityManager", em); return service; }
    private Member member(long id) {
        Member m = Member.createForTest("m" + id + "@example.invalid", "회원");
        ReflectionTestUtils.setField(m, "id", id); return m;
    }
    private void code(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    private ProjectApplicationServiceImpl applicationsService() {
        return inject(new ProjectApplicationServiceImpl(projects, members, memberships, applications,
                null, null, null, null, null));
    }

    @Test void withdrawnOrSuspendedMemberCannotApplyAndProjectIsNotLocked() {
        for (boolean withdrawn : new boolean[]{true, false}) {
            Member m = member(2L);
            if (withdrawn) m.forceWithdraw(); else m.suspend(null, "정지 사유", LocalDateTime.now());
            when(members.findById(2L)).thenReturn(Optional.of(m));
            code(() -> applicationsService().apply(3L, 2L, null),
                    withdrawn ? ErrorCode.MEMBER_WITHDRAWN : ErrorCode.UNAUTHORIZED);
        }
        verifyNoInteractions(projects, applications, memberships);
    }

    @Test void applyLocksAndRefreshesMemberBeforeProjectLookup() {
        Member m = member(2L);
        when(members.findById(2L)).thenReturn(Optional.of(m));
        when(projects.findActiveById(3L)).thenReturn(Optional.empty());
        code(() -> applicationsService().apply(3L, 2L, null), ErrorCode.PROJECT_NOT_FOUND);
        var order = inOrder(members, em, projects);
        order.verify(members).findById(2L);
        order.verify(em).refresh(m, LockModeType.PESSIMISTIC_WRITE);
        order.verify(projects).findActiveById(3L);
    }

    @Test void bothRosterEntryPointsRejectInactiveMemberBeforeProjectAccess() {
        Member m = member(2L); m.forceWithdraw();
        when(members.findById(2L)).thenReturn(Optional.of(m));
        var service = inject(new ProjectMemberServiceImpl(members, projects, memberships));
        code(() -> service.addMember(3L, 2L, null), ErrorCode.MEMBER_WITHDRAWN);
        code(() -> service.addCreator(3L, 2L, null), ErrorCode.MEMBER_WITHDRAWN);
        verifyNoInteractions(projects, memberships);
    }

    @Test void bothRosterEntryPointsAcquireMemberBeforeProject() {
        Member m = member(2L);
        when(members.findById(2L)).thenReturn(Optional.of(m));
        when(projects.findActiveById(3L)).thenReturn(Optional.empty());
        var service = inject(new ProjectMemberServiceImpl(members, projects, memberships));
        code(() -> service.addMember(3L, 2L, null), ErrorCode.PROJECT_NOT_FOUND);
        code(() -> service.addCreator(3L, 2L, null), ErrorCode.PROJECT_NOT_FOUND);
        var order = inOrder(members, projects);
        order.verify(members).findById(2L); order.verify(projects).findActiveById(3L);
        order.verify(members).findById(2L); order.verify(projects).findActiveById(3L);
    }

    @Test void projectCreationRefreshesPreReadCreatorAndRejectsWithdrawalBeforeInsert() {
        Member m = member(2L);
        when(members.findByEmailAndDeletedAtIsNull(m.getEmail())).thenReturn(Optional.of(m));
        when(members.findById(2L)).thenReturn(Optional.of(m));
        doAnswer(call -> { m.forceWithdraw(); return null; }).when(em).refresh(m, LockModeType.PESSIMISTIC_WRITE);
        var service = inject(new ProjectCommandServiceImpl(null, projects, members, null, null, null, null, null));
        code(() -> service.create(null, null, m.getEmail()), ErrorCode.MEMBER_WITHDRAWN);
        var order = inOrder(members, em);
        order.verify(members).findByEmailAndDeletedAtIsNull(m.getEmail());
        order.verify(members).findById(2L); order.verify(em).refresh(m, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(projects);
    }

    @Test void decisionRefreshesStalePendingAfterMemberThenProjectLocks() {
        Member leader = member(1L), applicant = member(2L);
        Project p = project(leader); ReflectionTestUtils.setField(p, "id", 3L);
        ProjectApplication a = ProjectApplication.create(p, applicant, null, "지원");
        when(applications.findById(4L)).thenReturn(Optional.of(a));
        when(members.findById(2L)).thenReturn(Optional.of(applicant));
        when(projects.findActiveById(3L)).thenReturn(Optional.of(p));
        doAnswer(call -> { a.cancel(); return null; }).when(em).refresh(a, LockModeType.PESSIMISTIC_WRITE);
        code(() -> applicationsService().decide(4L, new ApplicationDecisionRequest(ApplicationStatus.ACCEPTED), leader.getEmail()),
                ErrorCode.APPLICATION_ALREADY_DECIDED);
        var order = inOrder(members, projects, em);
        order.verify(members).findById(2L); order.verify(em).refresh(applicant, LockModeType.PESSIMISTIC_WRITE);
        order.verify(projects).findActiveById(3L); order.verify(em).refresh(p, LockModeType.PESSIMISTIC_WRITE); order.verify(em).refresh(a, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(memberships);
    }

    @Test void unauthorizedDecisionDoesNotAcquireApplicantOrProjectLocks() {
        Project p = project(member(1L));
        ProjectApplication a = ProjectApplication.create(p, member(2L), null, "지원");
        when(applications.findById(4L)).thenReturn(Optional.of(a));
        code(() -> applicationsService().decide(4L, new ApplicationDecisionRequest(ApplicationStatus.ACCEPTED), "intruder"),
                ErrorCode.PROJECT_MEMBER_FORBIDDEN);
        verifyNoInteractions(members, projects, em);
    }
    @Test void ordinaryWithdrawalLocksRequesterBeforeProject() {
        Member m = member(2L);
        when(members.findByEmailAndDeletedAtIsNull(m.getEmail())).thenReturn(Optional.of(m));
        when(members.findById(2L)).thenReturn(Optional.of(m));
        when(projects.findActiveById(3L)).thenReturn(Optional.empty());
        WithdrawRequest request = new WithdrawRequest(); ReflectionTestUtils.setField(request, "projectId", 3L);
        var service = inject(new ProjectMemberServiceImpl(members, projects, memberships));
        code(() -> service.withdraw(request, m.getEmail()), ErrorCode.PROJECT_NOT_FOUND);
        var order = inOrder(members, em, projects);
        order.verify(members).findByEmailAndDeletedAtIsNull(m.getEmail());
        order.verify(members).findById(2L); order.verify(em).refresh(m, LockModeType.PESSIMISTIC_WRITE);
        order.verify(projects).findActiveById(3L);
        verifyNoInteractions(memberships);
    }

    @Test void expelAndLegacyDeleteLockProjectBeforeLookingUpMembership() {
        Member leader = member(1L);
        Project p = project(leader);
        when(projects.findActiveById(3L)).thenReturn(Optional.of(p));
        when(memberships.findByProjectIdAndMemberId(3L, 2L)).thenReturn(Optional.empty());
        var management = inject(new ProjectManagementServiceImpl(projects, memberships));
        code(() -> management.expelMember(3L, 2L, leader.getEmail()), ErrorCode.PROJECT_MEMBER_NOT_FOUND);
        DeleteRequest request = new DeleteRequest();
        ReflectionTestUtils.setField(request, "projectId", 3L); ReflectionTestUtils.setField(request, "memberId", 2L);
        var roster = inject(new ProjectMemberServiceImpl(members, projects, memberships));
        code(() -> roster.deleteProjectMember(request, leader.getEmail()), ErrorCode.PROJECT_MEMBER_NOT_FOUND);
        var order = inOrder(projects, em, memberships);
        order.verify(projects).findActiveById(3L); order.verify(em).refresh(p, LockModeType.PESSIMISTIC_WRITE);
        order.verify(memberships).findByProjectIdAndMemberId(3L, 2L);
        order.verify(projects).findActiveById(3L); order.verify(em).refresh(p, LockModeType.PESSIMISTIC_WRITE);
        order.verify(memberships).existsByProjectIdAndMemberId(3L, 2L);
        verifyNoInteractions(members);
    }
    @Test void acceptedStatusIsWrittenAfterNestedRosterAdmissionRefresh() {
        Member leader = member(1L), applicant = member(2L);
        Project p = project(leader); ReflectionTestUtils.setField(p, "id", 3L);
        ProjectApplication a = ProjectApplication.create(p, applicant, null, "지원");
        ReflectionTestUtils.setField(a, "id", 4L);
        when(applications.findById(4L)).thenReturn(Optional.of(a));
        when(members.findById(2L)).thenReturn(Optional.of(applicant));
        when(projects.findActiveById(3L)).thenReturn(Optional.of(p));
        var roster = mock(com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberService.class);
        // Model a nested refresh restoring the database's still-PENDING application state.
        doAnswer(call -> { a.updateStatus(ApplicationStatus.PENDING); return null; })
                .when(roster).addMember(3L, 2L, null);
        var notifications = mock(com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService.class);
        var publisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        var service = inject(new ProjectApplicationServiceImpl(projects, members, memberships, applications,
                roster, null, null, notifications, publisher));
        service.decide(4L, new ApplicationDecisionRequest(ApplicationStatus.ACCEPTED), leader.getEmail());
        assertThat(a.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    private Project project(Member creator) {
        return Project.builder().creator(creator).name("테스트 프로젝트").description("소개")
                .projectCategory(com.wardk.meeteam_backend.domain.project.entity.ProjectCategory.CAPSTONE)
                .platformCategory(com.wardk.meeteam_backend.domain.project.entity.PlatformCategory.WEB)
                .recruitmentStatus(com.wardk.meeteam_backend.domain.project.entity.Recruitment.RECRUITING)
                .recruitmentDeadlineType(com.wardk.meeteam_backend.domain.project.entity.RecruitmentDeadlineType.END_DATE)
                .startDate(java.time.LocalDate.now()).endDate(java.time.LocalDate.now().plusMonths(1)).build();
    }
}
