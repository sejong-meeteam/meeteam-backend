package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecruitmentResponse {

    private JobField jobField;
    private JobPosition jobPosition;
    private int recruitmentCount;
    private int currentCount;
    private boolean isClosed;

    public static RecruitmentResponse responseDto(RecruitmentState recruitment) {
        JobPosition position = recruitment.getJobPosition();
        return RecruitmentResponse.builder()
                .jobField(recruitment.getJobField())
                .jobPosition(position)
                .recruitmentCount(recruitment.getRecruitmentCount())
                .currentCount(recruitment.getCurrentCount())
                .isClosed(recruitment.getCurrentCount() >= recruitment.getRecruitmentCount())
                .build();
    }
}
