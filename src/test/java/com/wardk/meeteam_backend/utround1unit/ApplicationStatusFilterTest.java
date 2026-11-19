package com.wardk.meeteam_backend.utround1unit;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationService;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.fixture.ProjectFixture;
import com.wardk.meeteam_backend.web.application.dto.response.ProjectApplicationListResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApplicationStatusFilterTest {

    private static final String LEADER_EMAIL = "status-filter-leader@example.invalid";

    @Autowired EntityManager em;
    @Autowired ProjectApplicationService applicationService;

    private Long projectId;

    @BeforeEach
    void saveOneApplicationPerStatus() {
        JobField backend = persist(JobField.of(JobFieldCode.BACKEND));
        JobPosition recruiting = persist(JobPosition.of(backend, JobPositionCode.JAVA_SPRING));
        JobPosition deleted = persist(JobPosition.of(backend, JobPositionCode.KOTLIN_SPRING));

        Project project = ProjectFixture.defaultProject(member(LEADER_EMAIL, "리더"));
        project.addRecruitment(RecruitmentState.createRecruitmentState(recruiting, 3));
        projectId = persist(project).getId();

        apply(project, "대기", recruiting, null);
        apply(project, "승인", recruiting, ApplicationStatus.ACCEPTED);
        apply(project, "거절", recruiting, ApplicationStatus.REJECTED);
        apply(project, "취소", recruiting, ApplicationStatus.CANCELLED);
        apply(project, "삭제포지션거절", deleted, ApplicationStatus.REJECTED);
        em.flush();
        em.clear();
    }

    @Test
    void pendingListShowsOnlyUndecidedApplicantsWithoutDecisionTime() {
        List<ProjectApplicationListResponse> list = list(ApplicationStatus.PENDING);

        assertThat(list).extracting(ProjectApplicationListResponse::applicantName).containsExactly("대기");
        assertThat(list.get(0).decidedAt()).isNull();
        assertThat(list.get(0).recruitmentCount()).isEqualTo(3);
    }

    @Test
    void acceptedListShowsAcceptedApplicantsWithDecisionTime() {
        List<ProjectApplicationListResponse> list = list(ApplicationStatus.ACCEPTED);

        assertThat(list).extracting(ProjectApplicationListResponse::applicantName).containsExactly("승인");
        assertThat(list.get(0).decidedAt()).isNotNull();
        assertThat(list.get(0).status()).isEqualTo("승인됨");
    }

    @Test
    void rejectedListIsNewestDecisionFirstAndSurvivesADeletedPosition() {
        List<ProjectApplicationListResponse> list = list(ApplicationStatus.REJECTED);

        assertThat(list).extracting(ProjectApplicationListResponse::applicantName)
                .containsExactly("삭제포지션거절", "거절");
        assertThat(list).allSatisfy(item -> assertThat(item.decidedAt()).isNotNull());
        assertThat(list.get(0).recruitmentCount()).isNull();
        assertThat(list.get(0).isRecruitmentFull()).isNull();
        assertThat(list.get(1).recruitmentCount()).isEqualTo(3);
    }

    @Test
    void cancellingAnApplicationLeavesNoDecisionTime() {
        Member applicant = member("cancel-check@example.invalid", "취소확인");
        Project project = em.find(Project.class, projectId);
        ProjectApplication application = ProjectApplication.create(project, applicant,
                project.getRecruitments().get(0).getJobPosition(), "지원합니다");
        application.cancel();

        assertThat(application.getDecidedAt()).isNull();
    }

    private List<ProjectApplicationListResponse> list(ApplicationStatus status) {
        return applicationService.getApplicationList(projectId, status, LEADER_EMAIL);
    }

    private void apply(Project project, String name, JobPosition position, ApplicationStatus status) {
        ProjectApplication application = ProjectApplication.create(project,
                member(name + "@example.invalid", name), position, name + " 지원 동기입니다");
        if (status != null) {
            application.updateStatus(status);
        }
        persist(application);
    }

    private Member member(String email, String name) {
        return persist(Member.createForTest(email, name));
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
