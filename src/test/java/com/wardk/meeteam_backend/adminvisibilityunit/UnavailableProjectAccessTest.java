package com.wardk.meeteam_backend.adminvisibilityunit;

import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationServiceImpl;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.qna.repository.*;
import com.wardk.meeteam_backend.domain.qna.service.ProjectQnaService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnavailableProjectAccessTest {
    @Test void hiddenAndDeletedApplicationsCannotBeDecidedOrCancelledThroughLegacyIds() {
        for (ProjectVisibility visibility : java.util.List.of(ProjectVisibility.HIDDEN, ProjectVisibility.DELETED)) {
            Project project = Project.builder().build();
            project.changeVisibility(visibility);
            var application = mock(ProjectApplication.class);
            when(application.getProject()).thenReturn(project);
            var applications = mock(ProjectApplicationRepository.class);
            when(applications.findById(9L)).thenReturn(Optional.of(application));
            var service = new ProjectApplicationServiceImpl(null, null, null, applications, null, null, null, null, null);
            assertThatThrownBy(() -> service.decide(9L, null, "leader@example.invalid"))
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
            assertThatThrownBy(() -> service.cancel(9L, 1L))
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
            verify(application, never()).cancel();
            verify(application, never()).updateStatus(any());
        }
    }

    @Test void unavailableProjectsCannotRevealLikeStatusOrBeLikedByEitherLegacyPath() {
        var projects = mock(ProjectRepository.class);
        var likes = mock(com.wardk.meeteam_backend.domain.projectlike.repository.ProjectLikeRepository.class);
        var members = mock(MemberRepository.class);
        when(projects.findActiveById(1L)).thenReturn(Optional.empty());
        when(projects.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        var service = new com.wardk.meeteam_backend.domain.projectlike.service.ProjectLikeService(projects, likes, members);
        java.util.List<Runnable> calls = java.util.List.of(
                () -> service.status(2L, 1L),
                () -> service.toggleWithOptimistic(1L, "member@example.invalid"),
                () -> service.toggleWithPessimistic(1L, "member@example.invalid"));
        for (Runnable call : calls) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        }
        verifyNoInteractions(likes, members);
    }

    @Test void everyQnaPathChecksPublicProjectBeforeReadingOrWritingQna() {
        var projects = mock(ProjectRepository.class);
        var questions = mock(ProjectQnaRepository.class);
        var answers = mock(QnaAnswerRepository.class);
        var members = mock(MemberRepository.class);
        when(projects.findActiveById(1L)).thenReturn(Optional.empty());
        var service = new ProjectQnaService(questions, answers, projects, members);
        java.util.List<Runnable> calls = java.util.List.of(
                () -> service.getQnaList(1L, PageRequest.of(0, 10), null),
                () -> service.createQuestion(1L, 2L, "Question", false),
                () -> service.addAnswer(1L, 3L, 2L, "Answer"),
                () -> service.deleteQuestion(1L, 3L, 2L),
                () -> service.deleteAnswer(1L, 3L, 4L, 2L));
        for (Runnable call : calls) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        }
        verifyNoInteractions(questions, answers, members);
    }
}
