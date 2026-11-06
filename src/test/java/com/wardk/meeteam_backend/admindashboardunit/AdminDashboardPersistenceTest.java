package com.wardk.meeteam_backend.admindashboardunit;

import com.wardk.meeteam_backend.domain.admin.entity.AdminAuditLog;
import com.wardk.meeteam_backend.domain.admin.entity.AuditAction;
import com.wardk.meeteam_backend.domain.admin.entity.AuditTargetType;
import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.repository.AdminDashboardRepository;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;
import com.wardk.meeteam_backend.domain.admin.service.AdminAuditQueryService;
import com.wardk.meeteam_backend.domain.admin.service.AdminDashboardService;

import com.wardk.meeteam_backend.domain.application.entity.*;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.job.entity.*;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.project.entity.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never",
                "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = AdminDashboardPersistenceTest.Config.class)
class AdminDashboardPersistenceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026,11,6,0,30);
    @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
    @Import({AdminDashboardService.class, AdminDashboardRepository.class, AdminQueryRepository.class,
            AdminResponseMapper.class, AdminAuditQueryService.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
        @Bean("adminClock") Clock adminClock() {
            // UTC is still November 5. The dashboard must nevertheless use the Seoul November 6 boundary.
            return Clock.fixed(Instant.parse("2026-11-05T15:30:00Z"), ZoneOffset.UTC);
        }
    }
    @Autowired EntityManager em;
    @Autowired AdminDashboardService dashboard;
    @MockBean S3ImageUrlResolver images;

    private Member member(String name, LocalDateTime created) {
        Member m = Member.createForTest(UUID.randomUUID() + "@example.invalid", name);
        ReflectionTestUtils.setField(m, "studentId", "20261234");
        ReflectionTestUtils.setField(m, "createdAt", created);
        em.persist(m); return m;
    }
    private Project project(Member leader, String name, ProjectVisibility visibility, Recruitment recruitment, LocalDateTime created) {
        Project p = Project.builder().creator(leader).name(name).description("소개")
                .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).recruitmentStatus(recruitment).build();
        p.changeVisibility(visibility); ReflectionTestUtils.setField(p, "createdAt", created); em.persist(p); return p;
    }
    @Test void emptyDatabaseReturnsZeroStatsAndEmptyArrays() {
        var result = dashboard.dashboard();
        assertThat(result.stats()).isEqualTo(new com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.DashboardStats(0,0,0,0,0,0,0));
        assertThat(result.recentMembers()).isEmpty(); assertThat(result.recentProjects()).isEmpty(); assertThat(result.recentAuditLogs()).isEmpty();
    }
    @Test void countsMatchFrontendSemanticsIncludingSeoulMidnightAndEffectiveSuspension() {
        var start = NOW.toLocalDate().atStartOfDay();
        Member admin = member("운영자", start); admin.changeRole(UserRole.ADMIN);
        Member ordinary = member("회원", start.plusMinutes(1));
        member("어제", start.minusNanos(1000));
        member("내일", start.plusDays(1));
        Member permanent = member("영구 정지", start.minusHours(1)); permanent.suspend(null, "정지 사유 확인", NOW);
        Member expired = member("만료", start.plusMinutes(2)); expired.suspend(1, "만료된 정지 사유", NOW.minusDays(1));
        Member withdrawn = member("탈퇴", start.plusMinutes(3)); withdrawn.forceWithdraw();
        Project visible = project(admin, "모집", ProjectVisibility.VISIBLE, Recruitment.RECRUITING, NOW);
        project(admin, "마감", ProjectVisibility.VISIBLE, Recruitment.CLOSED, NOW);
        Project hidden = project(admin, "숨김", ProjectVisibility.HIDDEN, Recruitment.RECRUITING, NOW);
        Project deleted = project(admin, "삭제", ProjectVisibility.DELETED, Recruitment.RECRUITING, NOW);
        JobField field = JobField.of(JobFieldCode.BACKEND); em.persist(field);
        JobPosition position = JobPosition.of(field, JobPositionCode.JAVA_SPRING); em.persist(position);
        for (Project project : List.of(visible, hidden, deleted)) em.persist(ProjectApplication.create(project, ordinary, position, "지원"));
        var accepted = ProjectApplication.create(visible, admin, position, "승인"); accepted.updateStatus(ApplicationStatus.ACCEPTED); em.persist(accepted);
        em.flush(); em.clear();
        var result = dashboard.dashboard();
        assertThat(result.stats()).isEqualTo(new com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.DashboardStats(6,3,1,3,1,1,3));
        assertThat(result.recentMembers()).hasSize(5).noneMatch(m -> m.status().equals("WITHDRAWN"));
        assertThat(result.recentMembers()).allMatch(m -> "2026****".equals(m.studentId()));
        assertThat(result.recentMembers()).filteredOn(m -> m.memberId().equals(expired.getId())).singleElement()
                .satisfies(m -> { assertThat(m.status()).isEqualTo("ACTIVE"); assertThat(m.suspendedUntil()).isNull(); });
        assertThat(result.recentProjects()).hasSize(3).noneMatch(p -> p.visibility().equals("DELETED"))
                .anyMatch(p -> p.visibility().equals("HIDDEN"));
        assertThat(em.find(Member.class, expired.getId()).getSuspendedUntil()).isEqualTo(NOW);
        assertThat(ReflectionTestUtils.getField(em.find(Member.class, expired.getId()), "suspended")).isEqualTo(true);
    }
    @Test void recentListsAreBoundedAndUseDescendingIdsToBreakEqualTimestampTies() {
        List<Long> members = new ArrayList<>(), projects = new ArrayList<>(), logs = new ArrayList<>();
        for (int i=0; i<7; i++) {
            Member member = member("회원" + i, NOW); members.add(member.getId());
            if (i == 6) member.suspend(null, "정지 사유 확인", NOW);
            Project p = project(member, "프로젝트" + i, i==6 ? ProjectVisibility.HIDDEN : ProjectVisibility.VISIBLE, Recruitment.RECRUITING, NOW);
            projects.add(p.getId());
            var log = new AdminAuditLog(member.getId(), member.getRealName(), AuditAction.PROJECT_HIDE,
                    AuditTargetType.PROJECT, p.getId(), p.getName(), "관리자 조치 사유", Map.of("index", i), NOW);
            em.persist(log); logs.add(log.getId());
        }
        em.flush(); em.clear();
        var result = dashboard.dashboard();
        Collections.reverse(members); Collections.reverse(projects); Collections.reverse(logs);
        assertThat(result.recentMembers()).extracting(m -> m.memberId()).containsExactlyElementsOf(members.subList(0,5));
        assertThat(result.recentProjects()).extracting(p -> p.projectId()).containsExactlyElementsOf(projects.subList(0,5));
        assertThat(result.recentAuditLogs()).extracting(a -> a.id()).containsExactlyElementsOf(logs.subList(0,5));
        assertThat(result.recentMembers().get(0).status()).isEqualTo("SUSPENDED");
        assertThat(result.recentMembers().get(0).suspendedUntil()).isNull();
    }
    @Test void recentListsPreferActualCreationTimeOverInsertionOrderAndExcludeWithdrawnDeleted() {
        Member newer = member("먼저 저장된 신규", NOW);
        Member older = member("나중 저장된 과거", NOW.minusDays(1));
        Member removed = member("삭제", NOW.plusDays(1)); removed.withdraw();
        Project newerProject = project(newer, "신규", ProjectVisibility.VISIBLE, Recruitment.RECRUITING, NOW);
        Project olderProject = project(older, "과거", ProjectVisibility.HIDDEN, Recruitment.CLOSED, NOW.minusDays(1));
        project(older, "삭제", ProjectVisibility.DELETED, Recruitment.RECRUITING, NOW.plusDays(1));
        em.flush(); em.clear();
        var result = dashboard.dashboard();
        assertThat(result.recentMembers()).extracting(m -> m.memberId()).containsExactly(newer.getId(), older.getId());
        assertThat(result.recentProjects()).extracting(p -> p.projectId()).containsExactly(newerProject.getId(), olderProject.getId());
    }
}
