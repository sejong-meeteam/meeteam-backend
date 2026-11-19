package com.wardk.meeteam_backend.utround1unit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.MeetingMode;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCollaboration;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.ProjectQueryService;
import com.wardk.meeteam_backend.fixture.MemberFixture;
import com.wardk.meeteam_backend.fixture.ProjectFixture;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSearchRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectMeetingModeSearchTest {

    @Autowired EntityManager em;
    @Autowired MemberRepository memberRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectQueryService projectQueryService;

    private Map<String, Long> ids;

    @BeforeEach
    void saveOneProjectPerMode() {
        Member leader = memberRepository.save(MemberFixture.withName("회의방식리더"));
        ids = Map.of(
                "ONLINE", save(leader, "온라인 프로젝트", MeetingMode.ONLINE),
                "OFFLINE", save(leader, "대면 프로젝트", MeetingMode.OFFLINE),
                "HYBRID", save(leader, "병행 프로젝트", MeetingMode.HYBRID),
                "NONE", save(leader, "미입력 프로젝트", null));
        em.flush();
        em.clear();
    }

    @Test
    void onlineFilterAlsoReturnsHybridProjects() {
        List<ProjectCardResponse> cards = search(MeetingMode.ONLINE);

        assertThat(ourProjects(cards)).containsExactlyInAnyOrder("ONLINE", "HYBRID");
        assertThat(cards).extracting(ProjectCardResponse::getMeetingMode)
                .containsOnly(MeetingMode.ONLINE, MeetingMode.HYBRID);
    }

    @Test
    void offlineFilterReturnsOnlyOfflineProjects() {
        List<ProjectCardResponse> cards = search(MeetingMode.OFFLINE);

        assertThat(ourProjects(cards)).containsExactly("OFFLINE");
        assertThat(cards).extracting(ProjectCardResponse::getMeetingMode).containsOnly(MeetingMode.OFFLINE);
    }

    @Test
    void hybridFilterReturnsOnlyHybridProjects() {
        List<ProjectCardResponse> cards = search(MeetingMode.HYBRID);

        assertThat(ourProjects(cards)).containsExactly("HYBRID");
        assertThat(cards).extracting(ProjectCardResponse::getMeetingMode).containsOnly(MeetingMode.HYBRID);
    }

    @Test
    void omittedFilterKeepsReturningEveryProjectIncludingThoseWithoutMode() {
        assertThat(ourProjects(search(null))).containsExactlyInAnyOrder("ONLINE", "OFFLINE", "HYBRID", "NONE");
    }

    private Long save(Member leader, String name, MeetingMode mode) {
        Project project = ProjectFixture.withName(name, leader);
        project.updateBasicInfo(project.getName(), project.getDescription(), project.getProjectCategory(),
                project.getPlatformCategory(), null, null, project.getEndDate(), project.getRecruitmentDeadlineType(),
                new ProjectCollaboration(mode, 4, null), null);
        return projectRepository.save(project).getId();
    }

    private List<ProjectCardResponse> search(MeetingMode mode) {
        var request = new ProjectSearchRequest(null, null, null, null, null, mode, null);
        return projectQueryService.searchV1(request, PageRequest.of(0, 100), null).getContent();
    }

    private Set<String> ourProjects(List<ProjectCardResponse> cards) {
        Map<Long, String> byId = ids.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        return cards.stream().map(ProjectCardResponse::getProjectId)
                .filter(byId::containsKey).map(byId::get).collect(Collectors.toSet());
    }
}
