package com.wardk.meeteam_backend.photounit;

import com.wardk.meeteam_backend.domain.job.entity.*;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.project.service.dto.ProjectEditCommand;
import com.wardk.meeteam_backend.domain.project.service.dto.RecruitmentEditCommand;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentTechStack;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

final class ProjectPhotoFixture {
    static final String EMAIL = "leader@example.invalid";
    static final String BASE = "https://bucket.example.invalid";
    static final String OLD_URL = BASE + "/images/old.png";
    static final Long PROJECT_ID = 10L;
    static final Long MEMBER_ID = 20L;
    static final Long RECRUITMENT_ID = 30L;
    static final Long TECH_ID = 40L;

    private ProjectPhotoFixture() {}

    static Member leader() {
        Member member = Member.createForTest(EMAIL, "Leader");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    static TechStack techStack() {
        TechStack techStack = TechStack.of("Java");
        ReflectionTestUtils.setField(techStack, "id", TECH_ID);
        return techStack;
    }

    static Project project(Member leader, String imageUrl) {
        Project project = Project.builder()
                .creator(leader).name("Original project").description("Original description")
                .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                .imageUrl(imageUrl).recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.RECRUITMENT_COMPLETED)
                .startDate(LocalDate.of(2026, 1, 1)).build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        JobPosition position = JobPosition.of(JobField.of(JobFieldCode.BACKEND), JobPositionCode.JAVA_SPRING);
        ReflectionTestUtils.setField(position, "id", 50L);
        RecruitmentState recruitment = RecruitmentState.createRecruitmentState(position, 2);
        ReflectionTestUtils.setField(recruitment, "id", RECRUITMENT_ID);
        recruitment.addRecruitmentTechStack(RecruitmentTechStack.create(techStack()));
        project.addRecruitment(recruitment);
        return project;
    }

    static ProjectEditCommand command(boolean removeImage) {
        return new ProjectEditCommand("Edited project", "Edited description", ProjectCategory.CAPSTONE,
                PlatformCategory.WEB, null, null, null, RecruitmentDeadlineType.RECRUITMENT_COMPLETED,
                JobPositionCode.JAVA_SPRING,
                List.of(new RecruitmentEditCommand(RECRUITMENT_ID, JobFieldCode.BACKEND,
                        JobPositionCode.JAVA_SPRING, 2, List.of(TECH_ID))), false, removeImage);
    }
}
