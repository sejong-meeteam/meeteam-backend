package com.wardk.meeteam_backend.nameprivacyunit;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationServiceImpl;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberServiceImpl;
import com.wardk.meeteam_backend.domain.qna.entity.ProjectQna;
import com.wardk.meeteam_backend.domain.qna.entity.QnaAnswer;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.application.dto.response.ApplicationDetailResponse;
import com.wardk.meeteam_backend.web.application.dto.response.ProjectApplicationListResponse;
import com.wardk.meeteam_backend.web.qna.dto.response.ProjectQnaResponse;
import com.wardk.meeteam_backend.web.qna.dto.response.QnaAnswerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NamePrivacyBoundaryTest {

    @Test
    void actualProjectCreatorGetsPrivateApplicantFullNameOnlyThroughProtectedFactories() {
        Member applicant = privateMember("applicant@example.invalid", "지원자실명");
        ProjectApplication application = applicationWith(applicant);

        assertThat(ApplicationDetailResponse.forAuthorizedProjectCreator(application).applicantName())
                .isEqualTo("지원자실명");
        assertThat(ProjectApplicationListResponse.forAuthorizedProjectCreator(application, mock(com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState.class)).applicantName())
                .isEqualTo("지원자실명");
    }

    @Test
    void authorizedCreatorServiceReturnsThePrivateApplicantFullName() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectApplicationRepository applications = mock(ProjectApplicationRepository.class);
        when(projects.findActiveById(10L)).thenReturn(Optional.of(projectOwnedBy("actual-creator@example.invalid")));
        ProjectApplication application = applicationWith(privateMember("applicant@example.invalid", "지원자실명"));
        when(applications.findByIdWithApplicantAndJobPosition(10L, 99L))
                .thenReturn(Optional.of(application));
        assertThat(applicationService(projects, applications)
                .getApplicationDetail(10L, 99L, "actual-creator@example.invalid").applicantName())
                .isEqualTo("지원자실명");
    }

    @Test
    void ordinaryTeamMemberCannotReadApplicantNamesEither() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectApplicationRepository applications = mock(ProjectApplicationRepository.class);
        when(projects.findActiveById(10L)).thenReturn(Optional.of(projectOwnedBy("actual-creator@example.invalid")));
        var service = applicationService(projects, applications);
        assertForbidden(() -> service.getApplicationList(10L, ApplicationStatus.PENDING, "ordinary-team-member@example.invalid"));
        assertForbidden(() -> service.getApplicationDetail(10L, 99L, "ordinary-team-member@example.invalid"));
        verifyNoInteractions(applications);
    }

    @Test
    void unrelatedLeaderCannotReadTargetProjectApplicantListOrDetail() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectApplicationRepository applications = mock(ProjectApplicationRepository.class);
        Project project = projectOwnedBy("actual-creator@example.invalid");
        when(projects.findActiveById(10L)).thenReturn(Optional.of(project));
        ProjectApplicationServiceImpl service = applicationService(projects, applications);

        assertForbidden(() -> service.getApplicationList(10L, ApplicationStatus.PENDING, "other-project-leader@example.invalid"));
        assertForbidden(() -> service.getApplicationDetail(10L, 99L, "other-project-leader@example.invalid"));
        verifyNoInteractions(applications);
    }

    @Test
    void actualCreatorCannotUseApplicationIdFromAnotherProject() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectApplicationRepository applications = mock(ProjectApplicationRepository.class);
        when(projects.findActiveById(10L)).thenReturn(Optional.of(projectOwnedBy("actual-creator@example.invalid")));
        when(applications.findByIdWithApplicantAndJobPosition(10L, 99L)).thenReturn(Optional.empty());
        ProjectApplicationServiceImpl service = applicationService(projects, applications);

        assertThatThrownBy(() -> service.getApplicationDetail(10L, 99L, "actual-creator@example.invalid"))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND));
        verify(applications).findByIdWithApplicantAndJobPosition(10L, 99L);
    }

    @Test
    void publicQnaAndTeamMemberResponsesMaskPrivateNames() {
        Member privateMember = privateMember("private@example.invalid", "비공개이름");
        ProjectQna qna = ProjectQna.create(null, privateMember, "질문", false);
        QnaAnswer answer = QnaAnswer.create(qna, privateMember, "답변");
        qna.addAnswer(answer);

        assertThat(ProjectQnaResponse.from(qna, 123L).questionerName()).isEqualTo("비****");
        assertThat(QnaAnswerResponse.from(answer, 123L).writerName()).isEqualTo("비****");

        ProjectMember projectMember = mock(ProjectMember.class);
        when(projectMember.getMember()).thenReturn(privateMember);
        when(projectMember.getRole()).thenReturn(ProjectMemberRole.MEMBER);
        Project project = mock(Project.class);
        when(project.getMembers()).thenReturn(List.of(projectMember));
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByIdWithMembers(10L)).thenReturn(Optional.of(project));

        ProjectMemberServiceImpl service = new ProjectMemberServiceImpl(null, projects, mock(ProjectMemberRepository.class));
        assertThat(service.getProjectMembers(10L).get(0).getName()).isEqualTo("비****");
    }

    private ProjectApplicationServiceImpl applicationService(ProjectRepository projects, ProjectApplicationRepository applications) {
        return new ProjectApplicationServiceImpl(projects, null, null, applications, null, null, null, null, null);
    }

    private Project projectOwnedBy(String email) {
        Member creator = Member.createForTest(email, "프로젝트생성자");
        return Project.builder().creator(creator).isDeleted(false).build();
    }

    private Member privateMember(String email, String name) {
        Member member = Member.createForTest(email, name);
        ReflectionTestUtils.setField(member, "namePrivate", true);
        ReflectionTestUtils.setField(member, "id", 7L);
        return member;
    }

    private ProjectApplication applicationWith(Member applicant) {
        ProjectApplication application = mock(ProjectApplication.class);
        JobPosition position = mock(JobPosition.class);
        JobField field = mock(JobField.class);
        when(position.getJobField()).thenReturn(field);
        when(application.getApplicant()).thenReturn(applicant);
        when(application.getJobPosition()).thenReturn(position);
        when(application.getStatus()).thenReturn(ApplicationStatus.PENDING);
        return application;
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROJECT_MEMBER_FORBIDDEN));
    }
}
