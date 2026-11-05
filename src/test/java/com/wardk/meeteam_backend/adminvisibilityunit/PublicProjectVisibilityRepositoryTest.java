package com.wardk.meeteam_backend.adminvisibilityunit;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.projectlike.repository.ProjectLikeRepository;
import com.wardk.meeteam_backend.domain.qna.repository.ProjectQnaRepository;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSearchCondition;
import com.wardk.meeteam_backend.web.mainpage.dto.request.CategoryCondition;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.*;

/** Isolated H2 only: does not load application configuration or contact a live database. */
@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:project-visibility;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = PublicProjectVisibilityRepositoryTest.Config.class)
class PublicProjectVisibilityRepositoryTest {
    @Configuration
    @EntityScan("com.wardk.meeteam_backend.domain")
    @EnableJpaRepositories(basePackageClasses = {ProjectRepository.class, ProjectApplicationRepository.class,
            ProjectMemberRepository.class, ProjectLikeRepository.class, ProjectQnaRepository.class,
            com.wardk.meeteam_backend.domain.member.repository.MemberRepository.class})
    static class Config {
        @Bean EntityManager entityManager(jakarta.persistence.EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
        @Bean JPAQueryFactory queryFactory(EntityManager em) { return new JPAQueryFactory(em); }
    }
    @Autowired EntityManager em;
    @Autowired ProjectRepository projects;
    @Autowired com.wardk.meeteam_backend.domain.member.repository.MemberRepository members;

    @Test void publicSearchMainDetailAndManagementExcludeHiddenAndDeletedButRawAdminLookupRemains() {
        Member creator = Member.createForTest("visibility@example.invalid", "Leader");
        em.persist(creator);
        Project visible = project(creator, ProjectVisibility.VISIBLE);
        Project hidden = project(creator, ProjectVisibility.HIDDEN);
        Project deleted = project(creator, ProjectVisibility.DELETED);
        em.flush(); em.clear();
        assertThat(projects.findAllWithCreatorAndSkills()).extracting(Project::getId).containsExactly(visible.getId());
        var paging = PageRequest.of(0, 10);
        assertThat(projects.searchWithSlice(new ProjectSearchCondition(), paging).getContent())
                .extracting(Project::getId).containsExactly(visible.getId());
        assertThat(projects.findAllSlicedForSearchAtCondition(new ProjectSearchCondition(), paging).getTotalElements()).isEqualTo(1);
        assertThat(projects.findProjectsFromMainPageCondition(new CategoryCondition(null), paging).getTotalElements()).isEqualTo(1);
        for (Project unavailable : java.util.List.of(hidden, deleted)) {
            Long id = unavailable.getId();
            assertThat(projects.findActiveById(id)).isEmpty();
            assertThat(projects.findProjectDetailById(id)).isEmpty();
            assertThat(projects.findProjectForEdit(id)).isEmpty();
            assertThat(projects.findByIdWithMembers(id)).isEmpty();
            assertThat(projects.findProjectWithLikeCount(id)).isEmpty();
            assertThat(projects.findByIdForUpdate(id)).isEmpty();
            assertThat(projects.findById(id)).isPresent();
        }
        hidden = projects.findById(hidden.getId()).orElseThrow();
        hidden.changeVisibility(ProjectVisibility.VISIBLE);
        em.flush(); em.clear();
        assertThat(projects.findProjectDetailById(hidden.getId())).isPresent();
        assertThat(projects.searchWithSlice(new ProjectSearchCondition(), paging).getContent()).hasSize(2);
    }

    private Project project(Member creator, ProjectVisibility visibility) {
        Project project = Project.builder().creator(creator).name(visibility.name())
                .recruitmentStatus(Recruitment.SUSPENDED)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();
        project.changeVisibility(visibility);
        em.persist(project);
        return project;
    }

    @Test void publicMemberSearchAndMainOnlyIncludeActiveOrdinaryMembers() {
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        Member active = Member.createForTest("ordinary@example.invalid", "일반 회원");
        Member expired = Member.createForTest("expired@example.invalid", "기간 만료 회원");
        expired.suspend(1, "과거 정지", now.minusDays(2));
        Member suspended = Member.createForTest("suspended@example.invalid", "정지 회원");
        suspended.suspend(null, "운영 정책 위반", now);
        Member withdrawn = Member.createForTest("withdrawn@example.invalid", "탈퇴 회원");
        withdrawn.withdraw();
        Member admin = Member.createForTest("admin@example.invalid", "관리자");
        org.springframework.test.util.ReflectionTestUtils.setField(admin, "role", com.wardk.meeteam_backend.domain.member.entity.UserRole.ADMIN);
        for (var member : java.util.List.of(active, expired, suspended, withdrawn, admin)) em.persist(member);
        em.flush(); em.clear();
        var paging = PageRequest.of(0, 20);
        assertThat(members.searchMembersV1(null, null, java.util.List.of(), paging).getContent())
                .extracting(Member::getId).containsExactlyInAnyOrder(active.getId(), expired.getId());
        assertThat(members.findAllWithProjectCountSort(paging).getContent())
                .extracting(Member::getId).containsExactlyInAnyOrder(active.getId(), expired.getId());
        assertThat(members.searchMembersV1(null, null, java.util.List.of(), PageRequest.of(0,20,org.springframework.data.domain.Sort.by("projectCount").descending())).getTotalElements()).isEqualTo(2);
    }
}
