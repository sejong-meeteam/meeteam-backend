package com.wardk.meeteam_backend.utround1unit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.MeetingMode;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCollaboration;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.fixture.MemberFixture;
import com.wardk.meeteam_backend.fixture.ProjectFixture;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectDetailResponse;
import jakarta.persistence.EntityManager;
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
class ProjectCollaborationPersistenceTest {

    @Autowired EntityManager em;
    @Autowired MemberRepository memberRepository;
    @Autowired ProjectRepository projectRepository;

    @Test
    void savedCollaborationIsReturnedOnDetailAndCard() {
        Long id = save(new ProjectCollaboration(MeetingMode.HYBRID, 6, "화요일 저녁 7시 디스코드"));

        Project reloaded = projectRepository.findById(id).orElseThrow();
        ProjectDetailResponse detail = ProjectDetailResponse.from(reloaded, false, false);
        ProjectCardResponse card = ProjectCardResponse.from(reloaded, List.of(), false);

        assertThat(detail.meetingMode()).isEqualTo(MeetingMode.HYBRID);
        assertThat(detail.weeklyHours()).isEqualTo(6);
        assertThat(detail.meetingNote()).isEqualTo("화요일 저녁 7시 디스코드");
        assertThat(card.getMeetingMode()).isEqualTo(MeetingMode.HYBRID);
        assertThat(card.getWeeklyHours()).isEqualTo(6);
    }

    @Test
    void projectWithoutCollaborationReturnsNullFields() {
        Long id = save(null);

        ProjectDetailResponse detail = ProjectDetailResponse.from(projectRepository.findById(id).orElseThrow(), false, false);

        assertThat(detail.meetingMode()).isNull();
        assertThat(detail.weeklyHours()).isNull();
        assertThat(detail.meetingNote()).isNull();
    }

    @Test
    void editReplacesCollaborationIncludingClearingIt() {
        Long id = save(new ProjectCollaboration(MeetingMode.OFFLINE, 10, "학교 도서관"));

        Project project = projectRepository.findById(id).orElseThrow();
        update(project, new ProjectCollaboration(MeetingMode.ONLINE, null, null));
        em.flush();
        em.clear();
        assertThat(projectRepository.findById(id).orElseThrow().getCollaboration())
                .isEqualTo(new ProjectCollaboration(MeetingMode.ONLINE, null, null));

        update(projectRepository.findById(id).orElseThrow(), ProjectCollaboration.EMPTY);
        em.flush();
        em.clear();
        assertThat(projectRepository.findById(id).orElseThrow().getCollaboration())
                .isEqualTo(ProjectCollaboration.EMPTY);
    }

    private Long save(ProjectCollaboration collaboration) {
        Member leader = memberRepository.save(MemberFixture.withName("협업리더"));
        Project project = ProjectFixture.defaultProject(leader);
        if (collaboration != null) {
            update(project, collaboration);
        }
        Long id = projectRepository.save(project).getId();
        em.flush();
        em.clear();
        return id;
    }

    private static void update(Project project, ProjectCollaboration collaboration) {
        project.updateBasicInfo(project.getName(), project.getDescription(), project.getProjectCategory(),
                project.getPlatformCategory(), project.getGithubRepositoryUrl(), project.getCommunicationChannelUrl(),
                project.getEndDate(), project.getRecruitmentDeadlineType(), collaboration, null);
    }
}
