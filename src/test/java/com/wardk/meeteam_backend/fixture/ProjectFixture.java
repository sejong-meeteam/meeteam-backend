package com.wardk.meeteam_backend.fixture;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import com.wardk.meeteam_backend.domain.project.entity.RecruitmentDeadlineType;

import java.time.LocalDate;

/**
 * Project 테스트 픽스처.
 */
public class ProjectFixture {

    public static Project defaultProject(Member creator) {
        return Project.builder()
                .creator(creator)
                .name("테스트 프로젝트")
                .description("테스트 프로젝트 설명")
                .projectCategory(ProjectCategory.CAPSTONE)
                .platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .isDeleted(false)
                .build();
    }

    public static Project withName(String name, Member creator) {
        return Project.builder()
                .creator(creator)
                .name(name)
                .description(name + " 설명")
                .projectCategory(ProjectCategory.CAPSTONE)
                .platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .isDeleted(false)
                .build();
    }

    public static Project withEndDate(LocalDate endDate, Member creator, String name) {
        return Project.builder()
                .creator(creator)
                .name(name)
                .description(name + " 설명")
                .projectCategory(ProjectCategory.CAPSTONE)
                .platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(endDate)
                .isDeleted(false)
                .build();
    }

    public static Project withCategory(ProjectCategory category, Member creator, String name) {
        return Project.builder()
                .creator(creator)
                .name(name)
                .description(name + " 설명")
                .projectCategory(category)
                .platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .isDeleted(false)
                .build();
    }

    public static Project withPlatform(PlatformCategory platform, Member creator, String name) {
        return Project.builder()
                .creator(creator)
                .name(name)
                .description(name + " 설명")
                .projectCategory(ProjectCategory.CAPSTONE)
                .platformCategory(platform)
                .recruitmentStatus(Recruitment.RECRUITING)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .isDeleted(false)
                .build();
    }

    public static Project withRecruitment(Recruitment recruitment, Member creator, String name) {
        return Project.builder()
                .creator(creator)
                .name(name)
                .description(name + " 설명")
                .projectCategory(ProjectCategory.CAPSTONE)
                .platformCategory(PlatformCategory.WEB)
                .recruitmentStatus(recruitment)
                .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .isDeleted(false)
                .build();
    }
}
