package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MyProjectResponse {

    private Long projectId;
    private String projectName;
    private Recruitment projectStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    private JobPosition jobPosition;

    public static MyProjectResponse responseDto(ProjectMember pm) {
        return MyProjectResponse.builder()
                .projectId(pm.getProject().getId())
                .projectName(pm.getProject().getName())
                .projectStatus(pm.getProject().getRecruitmentStatus())
                .startDate(pm.getProject().getStartDate())
                .endDate(pm.getProject().getEndDate())
                .jobPosition(pm.getJobPosition())
                .build();
    }
}
