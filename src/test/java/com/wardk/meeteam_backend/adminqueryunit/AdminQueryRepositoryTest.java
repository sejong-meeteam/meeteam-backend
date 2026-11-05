package com.wardk.meeteam_backend.adminqueryunit;

import com.wardk.meeteam_backend.domain.admin.*;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.*;

/** Isolated, embedded H2 only. Does not load the application's configuration or secrets. */
@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-query;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
        replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = AdminQueryRepositoryTest.Config.class)
class AdminQueryRepositoryTest {
    @Configuration
    @EntityScan("com.wardk.meeteam_backend.domain")
    @Import({AdminQueryRepository.class, AdminQueryService.class, AdminResponseMapper.class})
    static class Config {
        @org.springframework.context.annotation.Bean
        EntityManager entityManager(jakarta.persistence.EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
    }
    @Autowired EntityManager em;
    @Autowired AdminQueryRepository queries;
    @Autowired AdminQueryService service;
    @org.springframework.boot.test.mock.mockito.MockBean
    com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver images;

    private Project project(Member creator, String name) {
        Project project = Project.builder().creator(creator).name(name).description("Description")
                .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING).recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();
        em.persist(project);
        return project;
    }

    private com.wardk.meeteam_backend.domain.job.entity.JobPosition position() {
        var field = com.wardk.meeteam_backend.domain.job.entity.JobField.of(
                com.wardk.meeteam_backend.domain.job.entity.JobFieldCode.BACKEND);
        em.persist(field);
        var position = com.wardk.meeteam_backend.domain.job.entity.JobPosition.of(field,
                com.wardk.meeteam_backend.domain.job.entity.JobPositionCode.JAVA_SPRING);
        em.persist(position);
        return position;
    }

    @Test void appliesLiteralKeywordStatusAndPaginationInDatabase() {
        Member first = Member.createForTest("one@example.com", "Literal%_Name");
        Member second = Member.createForTest("two@example.com", "LiteralXXName");
        em.persist(first); em.persist(second); em.flush(); em.clear();
        var found = queries.members(AdminQueryOptions.of("0", "1", "%_", "name,asc"), null, "invalid", "invalid");
        assertThat(found.total()).isEqualTo(1);
        assertThat(found.content()).extracting(r -> r.member().getId()).containsExactly(first.getId());
        var secondPage = queries.members(AdminQueryOptions.of("1", "1", null, "name,asc"), null, null, null);
        assertThat(secondPage.total()).isEqualTo(2);
        assertThat(secondPage.content()).hasSize(1);
        assertThat(queries.members(AdminQueryOptions.of(null, null, null, null), "SUSPENDED", null, null).total()).isZero();
    }
    @Test void projectsUseRealEnumsCountsAndVisibility() {
        Member creator = Member.createForTest("creator@example.com", "Leader"); em.persist(creator);
        Project project = Project.builder().creator(creator).name("Project").description("Description")
                .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.SUSPENDED).recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();
        em.persist(project); em.flush(); em.clear();
        var options = AdminQueryOptions.of(null, null, "leader", "pendingApplicationCount,desc");
        var found = queries.projects(options, "CAPSTONE", "SUSPENDED", "VISIBLE");
        assertThat(found.total()).isEqualTo(1);
        assertThat(found.content().get(0).currentMembers()).isEqualTo(1);
        assertThat(found.content().get(0).targetMembers()).isEqualTo(1);
        assertThat(found.content().get(0).pendingCount()).isZero();
        assertThat(queries.projects(options, null, null, "HIDDEN").total()).isZero();
        assertThat(queries.project(project.getId())).isPresent();
        assertThat(queries.member(creator.getId()).orElseThrow().projectCount()).isEqualTo(1);
        assertThat(queries.memberProjects(creator.getId())).hasSize(1);
        assertThat(queries.applicationStats(project.getId())).containsEntry("ACCEPTED", 0L);
        assertThat(queries.members(AdminQueryOptions.of(null, null, null, "projectCount,desc"), null, null, null).content()).hasSize(1);
    }

    @Test void invalidFiltersFollowHandoffDefaultsAndWithdrawnFilterIsReal() {
        Member active = Member.createForTest("active@example.invalid", "Active");
        Member withdrawn = Member.createForTest("withdrawn@example.invalid", "Withdrawn");
        withdrawn.withdraw();
        em.persist(active); em.persist(withdrawn); em.flush(); em.clear();
        var options = AdminQueryOptions.of(null, null, null, "invalid-sort");
        assertThat(queries.members(options, "WITHDRAWN", null, null).content())
                .extracting(r -> r.member().getId()).containsExactly(withdrawn.getId());
        assertThat(queries.members(options, "ACTIVE", "USER", null).total()).isEqualTo(1);
        assertThat(queries.members(options, "invalid-status", "invalid-role", "invalid-field").total()).isEqualTo(2);
    }

    @Test void detailUsesFullStudentIdAndRepresentativeFieldMatchesListFiltering() {
        Member member = Member.createForTest("detail@example.invalid", "Detail");
        org.springframework.test.util.ReflectionTestUtils.setField(member, "studentId", "20261234");
        em.persist(member);
        var pos = position();
        em.persist(new com.wardk.meeteam_backend.domain.member.entity.MemberJobPosition(member, pos));
        var created = project(member, "Owned project");
        em.flush(); em.clear();
        var page = service.members(AdminQueryOptions.of(null, null, null, null), null, null, "BACKEND");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).studentId()).isEqualTo("2026****");
        assertThat(page.content().get(0).jobFieldCode()).isEqualTo("BACKEND");
        var detail = service.member(member.getId());
        assertThat(detail.summary().studentId()).isEqualTo("20261234");
        assertThat(detail.summary().projectCount()).isEqualTo(1);
        assertThat(detail.projects()).extracting(p -> p.projectId()).containsExactly(created.getId());
        assertThat(detail.projects().get(0).isLeader()).isTrue();
        assertThat(detail.jobPositions()).containsExactly(pos.getName());
        assertThat(queries.members(AdminQueryOptions.of(null, null, null, null), null, null, "FRONTEND").total()).isZero();
    }

    @Test void countsMembersOnceAndAggregatesApplicationsForDetailAndSort() {
        Member leader = Member.createForTest("owner@example.invalid", "Owner");
        Member teammate = Member.createForTest("mate@example.invalid", "Mate");
        em.persist(leader); em.persist(teammate);
        var pos = position();
        Project one = project(leader, "One");
        Project two = project(leader, "Two");
        var ownerLink = com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember.createProjectMember(
                leader, pos, com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole.LEADER);
        ownerLink.assignProject(one); em.persist(ownerLink);
        for (int i = 0; i < 2; i++) {
            var link = com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember.createProjectMember(
                    teammate, pos, com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole.MEMBER);
            link.assignProject(one); em.persist(link);
        }
        em.persist(com.wardk.meeteam_backend.domain.application.entity.ProjectApplication.create(one, teammate, pos, "Application"));
        var rejected = com.wardk.meeteam_backend.domain.application.entity.ProjectApplication.create(one, leader, pos, "Rejected");
        rejected.reject(); em.persist(rejected);
        em.flush(); em.clear();
        var rows = queries.projects(AdminQueryOptions.of("0", "1", null, "pendingApplicationCount,desc"), null, null, null);
        assertThat(rows.total()).isEqualTo(2);
        assertThat(rows.content()).hasSize(1);
        assertThat(rows.content().get(0).project().getId()).isEqualTo(one.getId());
        assertThat(rows.content().get(0).currentMembers()).isEqualTo(2);
        var detail = service.project(one.getId());
        assertThat(detail.members()).hasSize(2);
        assertThat(detail.members()).filteredOn(m -> m.isLeader()).hasSize(1);
        assertThat(detail.applicationStats()).containsEntry("PENDING", 1L).containsEntry("REJECTED", 1L).containsEntry("ACCEPTED", 0L).containsEntry("CANCELLED", 0L);
        assertThat(service.member(teammate.getId()).applications()).hasSize(1);
        Project deleted = em.find(Project.class, two.getId()); deleted.delete(); em.flush(); em.clear();
        assertThat(queries.projects(AdminQueryOptions.of(null, null, null, null), null, null, "DELETED").total()).isEqualTo(1);
        assertThat(queries.member(leader.getId()).orElseThrow().projectCount()).isEqualTo(1);
    }

    @Test void missingTargetsUseAdminNotFoundCode() {
        assertThatThrownBy(() -> service.member(999999L)).isInstanceOfSatisfying(
                com.wardk.meeteam_backend.global.exception.CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(com.wardk.meeteam_backend.global.response.ErrorCode.ADMIN_TARGET_NOT_FOUND));
        assertThatThrownBy(() -> service.project(999999L)).isInstanceOfSatisfying(
                com.wardk.meeteam_backend.global.exception.CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(com.wardk.meeteam_backend.global.response.ErrorCode.ADMIN_TARGET_NOT_FOUND));
    }

    @Test void effectiveSuspensionAndLoginSortUseStoredValues() {
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        Member suspended = Member.createForTest("suspended@example.invalid", "정지 회원");
        suspended.suspend(null, "운영 정책 위반", now);
        Member expired = Member.createForTest("expired@example.invalid", "만료 회원");
        expired.suspend(1, "과거 정지", now.minusDays(2));
        expired.recordLogin(now.minusDays(1));
        Member recent = Member.createForTest("recent@example.invalid", "최근 로그인");
        recent.recordLogin(now);
        em.persist(suspended); em.persist(expired); em.persist(recent); em.flush(); em.clear();
        var options = AdminQueryOptions.of(null, null, null, "lastLoginAt,desc");
        assertThat(service.members(options, "SUSPENDED", null, null).content())
                .extracting(r -> r.memberId()).containsExactly(suspended.getId());
        assertThat(service.members(options, "ACTIVE", null, null).content())
                .extracting(r -> r.memberId()).containsExactly(recent.getId(), expired.getId());
        assertThat(service.member(suspended.getId()).suspendReason()).isEqualTo("운영 정책 위반");
        assertThat(service.member(expired.getId()).suspendReason()).isNull();
    }
}
